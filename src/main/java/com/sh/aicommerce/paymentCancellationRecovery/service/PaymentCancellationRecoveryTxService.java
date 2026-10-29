package com.sh.aicommerce.paymentCancellationRecovery.service;


import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.paymentCancellation.PaymentCancellationException;
import com.sh.aicommerce.common.exception.recovery.PaymentCancellationRecoveryException;
import com.sh.aicommerce.common.exception.recovery.PaymentCancellationRecoveryHistoryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryResult;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryStatus;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentCancellationRecovery.dto.record.PaymentCancellationRecoveryTarget;
import com.sh.aicommerce.paymentCancellationRecovery.dto.request.PaymentCancellationRequestDto;
import com.sh.aicommerce.paymentCancellationRecovery.repository.PaymentCancellationRecoveryHistoryRepository;
import com.sh.aicommerce.paymentCancellationRecovery.repository.PaymentCancellationRecoveryRepository;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCancellationRecoveryTxService {
    private static final int RECOVERY_ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final EntityManager entityManager;
    private final PaymentCancellationRepository cancellationRepository;
    private final PaymentCancellationRecoveryRepository recoveryRepository;
    private final PaymentCancellationRecoveryHistoryRepository historyRepository;
    private final TossPaymentLogRepository logRepository;
    @Transactional
    public List<Long> findUnknownPaymentCancellation() {
        return cancellationRepository.findUnknownPaymentCancellation();
    }

    /**
     * 데이터 검증 및 Recovery 생성
     * - Recovery에 대한 값이 있으면 기존 데이터 생성
     * @param paymentCancellationId
     */
    @Transactional
    public PaymentCancellationRequestDto validateAndCreateRecovery(Long paymentCancellationId) {
        PaymentCancellation paymentCancellation = cancellationRepository.findByIdForUpdate(paymentCancellationId)
                .orElseThrow(() -> new PaymentCancellationException("해당 결제 취소 내역이 존재하지 않습니다."));

        OrderCancellation orderCancellation = paymentCancellation.getOrderCancellation();

        Payment payment = paymentCancellation.getPayment();
        Orders order = orderCancellation.getOrder();


        if(paymentCancellation.getStatus() != PaymentCancellationStatus.UNKNOWN)
            throw new PaymentCancellationException("현재 복구 작업을 처리 할 수 있는 상태가 아닙니다.");

        if(orderCancellation.getCancelStatus() != OrderCancelStatus.PROCESSING)
            throw new OrderCancellationException("현재 주문 취수 복구 처리가 가능한 상태가 아닙니다.");

        if (payment == null || order == null) {
            throw new PaymentCancellationException(
                    "[결제 취소/RECOVERY_VALIDATE] 주문 또는 결제 연결 정보가 없습니다.");
        }

        if (!Objects.equals(payment.getOrder().getId(), order.getId())) {
            throw new PaymentCancellationException(
                    "[결제 취소/RECOVERY_VALIDATE] 결제와 취소 요청의 주문이 일치하지 않습니다.");
        }

        if (!StringUtils.hasText(payment.getPaymentKey())) {
            throw new PaymentCancellationException(
                    "[결제 취소/RECOVERY_VALIDATE] PG 결제키가 없습니다.");
        }

        if (!StringUtils.hasText(paymentCancellation.getIdempotencyKey())) {
            throw new PaymentCancellationException(
                    "[결제 취소/RECOVERY_VALIDATE] 취소 멱등성 키가 없습니다.");
        }

        if (paymentCancellation.getCancelAmount() == null
                || paymentCancellation.getCancelAmount() <= 0
                || !Objects.equals(
                paymentCancellation.getCancelAmount(),
                payment.getAmount())) {
            throw new PaymentCancellationException(
                    "[결제 취소/RECOVERY_VALIDATE] 전체 취소 요청 금액이 결제 금액과 일치하지 않습니다.");
        }

        // PaymentCancellationRecovery 생성 || 기존에 존재하는 Recovery 사용
        PaymentCancellationRecovery recovery = recoveryRepository.findRecoveryByPaymentCancellationId(paymentCancellationId)
                .orElseGet(() -> {
                    PaymentCancellationRecovery created = PaymentCancellationRecovery.createRecovery(paymentCancellation, paymentCancellation.getStatus());
                    PaymentCancellationRecovery saved = recoveryRepository.save(created);

                    return saved;
                });


        return new PaymentCancellationRequestDto(recovery, paymentCancellation, payment, order);
    }

    @Transactional
    public Long startPaymentCancellationRecovery(Long recoveryId) {
        PaymentCancellationRecovery recovery = recoveryRepository.findRecoveryByPaymentCancellationIdForUpdate(recoveryId)
                .orElseThrow(() -> new PaymentCancellationRecoveryException("결제 취소 복구 내역이 존재하지 않습니다."));

        if(recovery.getRetryCount() >= recovery.getMaxRetryCount())
            return null;

        if(recovery.getStatus() != PaymentRecoveryStatus.WAITING &&
            recovery.getStatus() != PaymentRecoveryStatus.RETRY_WAIT)
            return null;

        recovery.startRecovery();

        PaymentCancellationRecoveryHistory history = PaymentCancellationRecoveryHistory.createHistory(recovery);
        historyRepository.save(history);

        return history.getId();
    }

    @Transactional
    public void applyPaymentCancellationResult(Long historyId, PaymentCancellationRequestDto dto, TossPaymentRequestHistoryResponseDto response) {
        PaymentCancellationRecoveryHistory history = historyRepository.findByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentCancellationRecoveryHistoryException("결제 취소 복구 내역이 존재하지 않습니다."));

        PaymentCancellationRecovery recovery = history.getPaymentCancellationRecovery();

        // 취소 응답 데이터 검증
        PaymentCancellationRecoveryTarget target = validatePaymentCancellationResult(history, recovery, response, dto);

        // 검증 통하면 상태 값 취소 상태로 변경
        applyPaymentCancellationSuccess(response, target);

    }

    private void applyPaymentCancellationSuccess(TossPaymentRequestHistoryResponseDto response, PaymentCancellationRecoveryTarget target) {
        CancelResponseDto cancelResponse = response.getCancels().get(0);

        PaymentCancellationRecovery recovery = target.recovery();
        PaymentCancellationRecoveryHistory history = target.history();
        PaymentCancellation cancellation = target.cancellation();
        Payment payment = target.payment();

        recovery.completeRecovery();
        history.completeRecoveryHistory(response.getStatus());
        payment.updatePaymentStatusToCanceled();
        cancellation.completeCancel(cancelResponse);

        TossPaymentLog tossPaymentLog = TossPaymentLog.createStatusToCancelSuccess(payment, response, cancellation.getId());
        logRepository.save(tossPaymentLog);
    }

    private PaymentCancellationRecoveryTarget validatePaymentCancellationResult(PaymentCancellationRecoveryHistory history, PaymentCancellationRecovery recovery, TossPaymentRequestHistoryResponseDto response, PaymentCancellationRequestDto dto) {
        if (history == null || recovery == null || response == null || dto == null) {
            throw invalidResult("복구 대상 또는 PG 조회 응답이 없습니다.");
        }
        if (!Objects.equals(dto.getRecoveryId(), recovery.getId())
                || !Objects.equals(history.getPaymentCancellationRecovery().getId(), recovery.getId())
                || recovery.getStatus() != PaymentRecoveryStatus.PROCESSING
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            throw invalidResult("현재 진행 중인 복구 실행 이력이 아닙니다.");
        }

        PaymentCancellation cancellation = recovery.getPaymentCancellation();
        OrderCancellation orderCancellation = cancellation.getOrderCancellation();
        // 취소 실패 반영과 동일하게 OrderCancellation → PaymentCancellation 순서로 잠금
        entityManager.refresh(orderCancellation, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(cancellation, LockModeType.PESSIMISTIC_WRITE);
        Payment payment = cancellation.getPayment();
        Orders order = orderCancellation.getOrder();
        if (payment == null || order == null || payment.getOrder() == null) {
            throw invalidResult("주문 또는 결제 연결 정보가 없습니다.");
        }
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!Objects.equals(dto.getPaymentId(), payment.getId())
                || !Objects.equals(dto.getOrderId(), order.getId())
                || !Objects.equals(payment.getOrder().getId(), order.getId())
                || !Objects.equals(dto.getOrderNumber(), order.getOrderNumber())
                || !StringUtils.hasText(cancellation.getIdempotencyKey())
                || !Objects.equals(dto.getIdempotencyKey(), cancellation.getIdempotencyKey())) {
            throw invalidResult("복구 요청과 주문·결제·취소 작업 연결이 일치하지 않습니다.");
        }
        if (cancellation.getStatus() != PaymentCancellationStatus.UNKNOWN
                && cancellation.getStatus() != PaymentCancellationStatus.PROCESSING) {
            throw invalidResult("결과 확인이 필요한 결제 취소 상태가 아닙니다.");
        }
        if (orderCancellation.getCancelStatus() != OrderCancelStatus.PROCESSING
                || payment.getStatus() != PaymentStatus.PAID) {
            throw invalidResult("주문 취소 또는 결제의 최신 상태가 복구 가능한 상태가 아닙니다.");
        }
        if (!"CANCELED".equals(response.getStatus())) {
            throw invalidResult("PG 거래가 전체 취소 완료 상태가 아닙니다.");
        }
        if (!StringUtils.hasText(payment.getPaymentKey())
                || !Objects.equals(payment.getPaymentKey(), response.getPaymentKey())
                || !Objects.equals(order.getOrderNumber(), response.getOrderId())) {
            throw invalidResult("PG 결제키 또는 주문번호가 내부 결제와 일치하지 않습니다.");
        }
        if (payment.getAmount() == null || payment.getAmount() <= 0
                || !Objects.equals(payment.getAmount(), cancellation.getCancelAmount())
                || !Objects.equals(payment.getAmount(), response.getTotalAmount())
                || !Integer.valueOf(0).equals(response.getBalanceAmount())
                || !"KRW".equals(response.getCurrency())) {
            throw invalidResult("원결제 금액·전체 취소 금액·잔액 또는 통화가 일치하지 않습니다.");
        }
        // 현재 전체 취소 1회 정책: 여러 취소 거래를 임의로 합쳐 이번 작업으로 확정하지 않는 방식
        if (response.getCancels() == null || response.getCancels().size() != 1) {
            throw invalidResult("전체 취소 정책에 맞는 단일 취소 거래가 없습니다.");
        }
        CancelResponseDto cancel = response.getCancels().get(0);
        if (cancel == null || !"DONE".equals(cancel.getCancelStatus())
                || cancel.getCanceledAt() == null
                || !StringUtils.hasText(cancel.getTransactionKey())
                || !Objects.equals(cancellation.getCancelAmount(), cancel.getCancelAmount())
                || !Integer.valueOf(0).equals(cancel.getRefundableAmount())) {
            throw invalidResult("PG 취소 거래의 완료 상태·일시·거래번호·금액이 유효하지 않습니다.");
        }
        // cancelReason은 기존 요청에서 고정 문구를 사용하므로 사용자 취소 사유와 비교하지 않는 범위
        // cancelRequestId는 null 가능, PG 조회 응답에는 내부 취소 멱등성 키가 없어 직접 비교 불가
        log.info("[PAYMENT_CANCEL] stage=RECOVERY_RESPONSE_VALIDATE event=VALIDATED recoveryId={} historyId={} paymentCancellationId={} paymentId={} orderNumber={} cancelAmount={}",
                recovery.getId(), history.getId(), cancellation.getId(), payment.getId(), order.getOrderNumber(), cancel.getCancelAmount());
        return new PaymentCancellationRecoveryTarget(cancellation,recovery, history, payment, order);
    }

    /** PG 조회 오류: 인증·설정 오류 등은 즉시 운영자 확인, 나머지는 최대 횟수까지 재조회 */
    @Transactional
    public void updateRecoveryQueryFailure(Long historyId, String errorCode, String errorMessage,
                                           boolean manualReview) {
        applyRecoveryFailure(historyId, errorCode, errorMessage, manualReview, false);
    }

    /** 결과 검증·DB 반영 실패는 롤백 종료 후 별도 트랜잭션에서 기록 */
    @Transactional
    public void updateRecoveryApplyFailure(Long historyId, RuntimeException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getClass().getSimpleName();
        applyRecoveryFailure(historyId, null, message, false, true);
    }

    private void applyRecoveryFailure(Long historyId, String errorCode, String errorMessage,
                                      boolean manualReview, boolean applyFailure) {
        log.warn("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE_APPLY event=START historyId={} pgCode={} applyFailure={}",
                historyId, errorCode, applyFailure);
        PaymentCancellationRecoveryHistory history = historyRepository.findByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentCancellationRecoveryHistoryException(
                        "[결제 취소/RECOVERY_FAILURE_APPLY] 복구 실행 이력이 없습니다."));
        PaymentCancellationRecovery recovery = history.getPaymentCancellationRecovery();
        entityManager.refresh(recovery, LockModeType.PESSIMISTIC_WRITE);
        if (recovery.getStatus() != PaymentRecoveryStatus.PROCESSING
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            log.info("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={} historyResult={}",
                    recovery.getId(), historyId, recovery.getStatus(), history.getResult());
            return;
        }
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "결제 취소 복구 중 오류 발생";
        if (message.length() > RECOVERY_ERROR_MESSAGE_MAX_LENGTH) {
            message = message.substring(0, RECOVERY_ERROR_MESSAGE_MAX_LENGTH);
        }
        // 실행 시작 시 이미 증가한 retryCount 사용. 취소 상태·주문·결제·재고는 변경하지 않는 범위
        if (manualReview || recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            recovery.updateRecoveryToManualCheck(errorCode, message);
        } else {
            recovery.updateRecoveryForNextRetry(errorCode, message);
        }
        if (applyFailure) {
            history.updateHistoryForApplyFailure(errorCode, message);
        } else {
            history.updateHistoryForNextRetry(errorCode, message);
        }
        log.warn("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE_APPLY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} historyResult={} pgCode={}",
                recovery.getId(), historyId, recovery.getRetryCount(), recovery.getMaxRetryCount(),
                recovery.getStatus(), history.getResult(), errorCode);
    }

    private PaymentCancellationRecoveryException invalidResult(String message) {
        return new PaymentCancellationRecoveryException("[결제 취소/RECOVERY_RESPONSE_VALIDATE] " + message);
    }
}
