package com.sh.aicommerce.common.scheduler.payment;


import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.payment.service.PaymentRetryTxService;
import com.sh.aicommerce.payment.service.PaymentTxService;
import com.sh.aicommerce.payment.service.UnknownPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentUnknownRetryScheduler {

    private final PaymentRepository paymentRepository;

    private final PaymentRetryTxService retryTxService;

    private final TossPaymentClient client;



    /**
     * UNKNOWN 결제 재처리 스케줄러
     *
     * 1. UNKNOWN 결제 조회
     * 2. 복구 대상 검증 및 생성
     * 3. 복구 시작 처리
     * 4. Toss 결제 결과 조회
     * 5. 조회 결과 DB 반영
     */
//    @Scheduled
    public void unknownPaymentRetry() {
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=START");
        List<Long> unknownPaymentList = retryTxService.getUnknownPaymentRetry();
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=CANDIDATES count={}", unknownPaymentList.size());

        // 결제 결과 확인 필요에 대한 상황이 없는 경우에는 스케줄러 종료
        if(unknownPaymentList.isEmpty())
            return;

        for (Long paymentId : unknownPaymentList) {
            try {
                log.info("[PAYMENT] stage=RECOVERY_ITEM event=START paymentId={}", paymentId);
                unknownPaymentDetailInfo(paymentId);
            } catch (Exception e) {
                log.error(
                        "[PAYMENT] stage=RECOVERY_ITEM event=FAILED paymentId={} errorType={}",
                        paymentId, e.getClass().getSimpleName()
                );
            }
        }
    }

    public void unknownPaymentDetailInfo(Long paymentId) {
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=START paymentId={}", paymentId);
        UnknownPaymentRequestDto dto = retryTxService.validatePayment(paymentId);
        if(dto == null) {
            log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=SKIPPED paymentId={}", paymentId);
            return;
        }
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=COMMITTED paymentId={} orderNumber={} attemptId={} recoveryId={}", dto.getPaymentId(), dto.getOrderNumber(), dto.getAttemptId(), dto.getRecoveryId());

        Long historyId = retryTxService.startPaymentRecovery(dto.getRecoveryId());

        if(historyId == null) {
            log.info("[PAYMENT] stage=RECOVERY_CLAIM event=SKIPPED paymentId={} recoveryId={}", paymentId, dto.getRecoveryId());
            return;
        }
        log.info("[PAYMENT] stage=RECOVERY_CLAIM event=COMMITTED paymentId={} recoveryId={} historyId={}", paymentId, dto.getRecoveryId(), historyId);

        //Unknown이 되는 상황에서는 DB 업데이트 문제가 생긴경우에는 해당 상태 값으로 변하지 않음. 외부 API 응답 문제 일수도 있기 떄문에
        // 하지만 그래도 토스 API 요청은 확인을 해봐야될거 같긴하다
        try {
            // 결제 조회에 대한 부분은 멱등성 키로 하는 것이 아닌 주문번호에 대한 값으로 확인
            TossPaymentRequestHistoryResponseDto paymentHistory = client.findPaymentHistory(dto.getOrderNumber());

            log.info("[PAYMENT] stage=RECOVERY_QUERY event=RESPONSE_RECEIVED paymentId={} recoveryId={} historyId={} pgStatus={}", paymentId, dto.getRecoveryId(), historyId, paymentHistory.getStatus());
            // PG 조회 결과가 있는 경우에는 DB 업데이트 결제 완료 및 실패로 변경
        } catch (TossPaymentException paymentException) {
            log.warn("[PAYMENT] stage=RECOVERY_QUERY event=HTTP_ERROR paymentId={} recoveryId={} historyId={} pgCode={}", paymentId, dto.getRecoveryId(), historyId, paymentException.getErrorCode());
            if (paymentException.getErrorCode().equals("NOT_FOUND_PAYMENT")) {
                // 결제 조회에 대한 내역이 존재하지 않으면, 현재 존재하는 Payment에 대한 값 업데이트
                retryTxService.updateFailPaymentRecovery(historyId, paymentException.getErrorCode(), paymentException.getMessage());
                log.info("[PAYMENT] stage=RECOVERY_APPLY event=COMMITTED paymentId={} recoveryId={} historyId={}", paymentId, dto.getRecoveryId(), historyId);
            }
        } catch (TossRestClientException restClientException) {
            log.warn("[PAYMENT] stage=RECOVERY_QUERY event=RESULT_UNKNOWN paymentId={} recoveryId={} historyId={} errorType={}", paymentId, dto.getRecoveryId(), historyId, restClientException.getClass().getSimpleName());

        }

    }
}
