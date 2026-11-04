package com.sh.aicommerce.paymentCompensation.service;

import com.sh.aicommerce.common.exception.paymentCompensation.PaymentCompensationException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationService;
import com.sh.aicommerce.paymentCompensation.record.PaymentCompensationRequestRecord;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationService {
    private static final String PAYMENT_LOCK_PREFIX = "lock:payment:orderNumber:";
    private static final java.time.Duration PAYMENT_LOCK_TTL = java.time.Duration.ofSeconds(60);
    private final org.springframework.data.redis.core.StringRedisTemplate paymentLockTemplate;
    private final com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository compensationRepository;
    private final PaymentCompensationTxService txService;
    private final TossPaymentClient client;
    private final PaymentCancellationService cancellationService;

    private static final int RETRY_BATCH_SIZE = 100;
    private static final java.time.Duration PENDING_RETRY_DELAY = java.time.Duration.ofMinutes(10);

    public void retryPendingCompensations() {
        LocalDateTime cutoff = java.time.LocalDateTime.now().minus(PENDING_RETRY_DELAY);
        List<Long> targets = compensationRepository.findPendingRetryIds(
                cutoff, org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE));

        log.info("[PAYMENT] stage=COMPENSATION_RETRY_SCAN event=CANDIDATES count={}", targets.size());

        for (Long compensationId : targets) {
            processCompensation(compensationId, cutoff);
        }
    }

    public void compensationProcess(Long compensationId) {
        processCompensation(compensationId, null);
    }

    /**
     * retryCutOff
     * - retryCutOff : null -> 스케줄러를 통해서 보상 트랜잭션을 재실행시키는 것이 아닌 초기에 보상 트랜잭션을 실행 시키는 경우
     * - retryCutOff : LocalDateTime.now().minustMinutes(10) -> 스케줄러를 통해서 보상 트랜잭션을 실행시키는 경우
     * @param compensationId
     * @param retryCutoff
     */
    private void processCompensation(Long compensationId, LocalDateTime retryCutoff) {
        String orderNumber = null;

        try {
            PaymentCompensationRequestRecord record = retryCutoff == null
                    ? txService.findPaymentAndAttempt(compensationId)
                    : txService.findPaymentAndAttemptForRetry(compensationId, retryCutoff);

            if (record == null)
                return;

            orderNumber = record.orderNumber();

            // 외부 조회·취소 호출은 보상 준비 DB 트랜잭션이 종료된 후 수행합니다.
            TossPaymentRequestHistoryResponseDto response = client.findPaymentHistory(record.orderNumber());
            /**
             * 결제 취소 API를 사용하기 위해서는 실제 승인이 된 결제 내역만 결제 취소 요청을 진행할 수 있음.
             * - 결제 조회 API를 사용했을 떄 나올 수 있는 상태 값
             * ABORT, EXPIRED 값은 둘다 모두 결제 승인이 되기 전 상태값
             */
            if (response != null && ("ABORTED".equals(response.getStatus()) || "EXPIRED".equals(response.getStatus()))) {
                completeUnapprovedWithLock(compensationId, orderNumber, response);
                return;
            }
            if (response == null || !("DONE".equals(response.getStatus()) || "CANCELED".equals(response.getStatus()))) {
                txService.defer(compensationId, "PG_RESULT_NOT_FINAL", false);
                return;
            }
            Long cancellationId = txService.prepareCancellation(compensationId, response);

            // 실제 취소 처리 하는 로직
            if (cancellationId != null) {
                // 기존 취소 서비스의 READY → PROCESSING 선점을 재사용합니다.
                cancellationService.process(cancellationId);
            }
        } catch (TossPaymentException e) {
            if ("NOT_FOUND_PAYMENT".equals(e.getErrorCode())) {
                try {
                    completeUnapprovedWithLock(compensationId, orderNumber, null);
                } catch (PaymentCompensationException validationException) {
                    saveFailure(compensationId, "COMPENSATION_VALIDATION_FAILED", true, validationException);
                } catch (RuntimeException applyException) {
                    saveFailure(compensationId, "PAYMENT_NOT_FOUND_APPLY_FAILED", false, applyException);
                }
            } else {
                saveFailure(compensationId, e.getErrorCode(), false, e);
            }
        } catch (PaymentCompensationException e) {
            saveFailure(compensationId, "COMPENSATION_VALIDATION_FAILED", true, e);
        } catch (RuntimeException e) {
            saveFailure(compensationId, "COMPENSATION_EXECUTION_FAILED", false, e);
        }
    }

    private void completeUnapprovedWithLock(Long compensationId, String orderNumber,
                                            TossPaymentRequestHistoryResponseDto response) {
        if (orderNumber == null) {
            throw new PaymentCompensationException("미승인 종료 대상 주문번호가 없습니다.");
        }
        String key = PAYMENT_LOCK_PREFIX + orderNumber;
        String token = java.util.UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(paymentLockTemplate.opsForValue().setIfAbsent(key, token, PAYMENT_LOCK_TTL))) {
            throw new com.sh.aicommerce.common.exception.payment.PaymentException("결제 처리 중이므로 미승인 종료를 보류합니다.");
        }
        try {
            if (response == null) txService.completePaymentNotFound(compensationId);
            else txService.completeUnapprovedPayment(compensationId, response);
        } finally {
            try {
                paymentLockTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0", Long.class),
                        java.util.List.of(key), token);
            } catch (RuntimeException unlockException) {
                log.error("[PAYMENT] stage=COMPENSATION_UNLOCK event=FAILED compensationId={} errorType={}",
                        compensationId, unlockException.getClass().getSimpleName());
            }
        }
    }

    private void saveFailure(Long compensationId, String code, boolean manual, RuntimeException cause) {
        log.error("[PAYMENT] stage=COMPENSATION event=FAILED compensationId={} errorCode={} errorType={}",
                compensationId, code, cause.getClass().getSimpleName());
        try {
            txService.defer(compensationId, code, manual);
        } catch (RuntimeException saveException) {
            log.error("[PAYMENT] stage=COMPENSATION_FAILURE_APPLY event=FAILED compensationId={} errorType={}",
                    compensationId, saveException.getClass().getSimpleName());
        }
    }
}
