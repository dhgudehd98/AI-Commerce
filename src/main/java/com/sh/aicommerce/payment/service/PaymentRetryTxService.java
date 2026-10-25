package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.paymentRecovery.PaymentRecoveryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryStatus;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.payment.repository.history.PaymentRecoveryHistoryRepository;
import com.sh.aicommerce.payment.repository.recovery.PaymentRecoveryRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRetryTxService {
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentRecoveryRepository recoveryRepository;
    private final PaymentRecoveryHistoryRepository recoverHistoryRepository;

    private final ProductInventoryService inventoryService;
    private final EntityManager entityManager;


    public List<Long> getUnknownPaymentRetry() {

        return paymentRepository.findPaymentStatusUnknown();
    }

    @Transactional
    public UnknownPaymentRequestDto validatePayment(Long paymentId) {

        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=TRANSACTION_START paymentId={}", paymentId);
        Payment payment =
                paymentRepository.findPaymentByIdWithOrder(paymentId)
                        .orElseThrow(() ->
                                new PaymentException(
                                        "결제 정보가 존재하지 않습니다."
                                )
                        );

        Orders order = payment.getOrder();

        PaymentAttempt attempt =
                attemptRepository
                        .findPaymentAttemptByOrderNumber(
                                order.getOrderNumber()
                        )
                        .orElseThrow(() ->
                                new PaymentAttemptException(
                                        "결제 승인 시도 내역이 존재하지 않습니다."
                                )
                        );

        if (payment.getStatus() != PaymentStatus.UNKNOWN
                || attempt.getStatus() != PaymentAttemptStatus.UNKNOWN
                || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {

            log.warn(
                    "[PAYMENT] stage=RECOVERY_VALIDATE event=STATE_MISMATCH paymentStatus={} attemptStatus={} orderStatus={}",
                    payment.getStatus(),
                    attempt.getStatus(),
                    order.getStatus()
            );

            return null;
        }

        if (attempt.getIdempotencyKey() == null) {
            log.warn(
                    "[PAYMENT] stage=RECOVERY_VALIDATE event=MISSING_IDEMPOTENCY_KEY attemptId={}",
                    attempt.getId()
            );
            return null;
        }

        PaymentRecovery recovery =
                recoveryRepository
                        .findPaymentRecoveryByAttemptIdForUpdate(
                                attempt.getId()
                        )
                        .orElseGet(() -> {
                            PaymentRecovery created =
                                    PaymentRecovery.createRecovery(attempt);
                            PaymentRecovery saved = recoveryRepository.save(created);
                            log.info("[PAYMENT] stage=RECOVERY_CREATE event=CREATED_PENDING_COMMIT paymentId={} attemptId={} recoveryId={}", paymentId, attempt.getId(), saved.getId());
                            return saved;
                        });

        return new UnknownPaymentRequestDto(
                payment, attempt, order, recovery
        );
    }

    /**
     * 복구 작업 시작
     * - retryCount 증가
     * - Recovery 상태값 변경
     * - RecoveryHistory 생성
     * @param recoveryId
     * @return
     */
    @Transactional
    public Long startPaymentRecovery(Long recoveryId) {
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(recoveryId)
                .orElseThrow(() -> new PaymentRecoveryException("현재 결제 복구 작업이 존재하지 않습니다."));

        if(recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            log.info("[PAYMENT] stage=RECOVERY_CLAIM event=RETRY_LIMIT recoveryId={} retryCount={} maxRetryCount={}", recoveryId, recovery.getRetryCount(), recovery.getMaxRetryCount());
            return null;
        }

        if(recovery.getStatus() != PaymentRecoveryStatus.WAITING
            && recovery.getStatus() != PaymentRecoveryStatus.RETRY_WAIT) {
            log.info("[PAYMENT] stage=RECOVERY_CLAIM event=STATE_SKIPPED recoveryId={} recoveryStatus={}", recoveryId, recovery.getStatus());
            return null;
        }

        // 재시도 횟수 증가 + 복구 상태 PROCESSING으로 변경
        recovery.startRecovery();

        // 현재 실행할 복구 history 생성
        PaymentRecoveryHistory history = PaymentRecoveryHistory.createHistory(recovery);
        recoverHistoryRepository.save(history);
        log.info("[PAYMENT] stage=RECOVERY_CLAIM event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={}", recoveryId, history.getId(), recovery.getRetryCount(), recovery.getMaxRetryCount(), recovery.getStatus());

        return history.getId();
    }

    @Transactional
    public void updatePaymentRecovery(UnknownPaymentRequestDto dto) {
        log.info("[PAYMENT] stage=RECOVERY_COMPLETE event=START recoveryId={}", dto.getRecoveryId());
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(dto.getRecoveryId())
                .orElseThrow(() -> new PaymentRecoveryException("결제 재시도 이력이 존재하지 않습니다."));
        recovery.updateRecovery();

        PaymentRecoveryHistory history = PaymentRecoveryHistory.createHistory(recovery);
    }

    /**
     * 결제 내역이 없는 경우
     *
     *
     * -
     * @param historyId
     * @param errorCode
     * @param errorMessage
     */
    @Transactional
    public void updateFailPaymentRecovery(Long historyId, String errorCode, String errorMessage) {
        log.info("[PAYMENT] stage=RECOVERY_APPLY event=START historyId={} pgCode={}", historyId, errorCode);

        PaymentRecoveryHistory history = recoverHistoryRepository.findHistoryByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentRecoveryException("결제 복구 history가 존재하지 않습니다."));
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(history.getPaymentRecovery().getId())
                .orElseThrow(() -> new PaymentRecoveryException("결제 복구 내역이 존재하지 않습니다."));


        if(recovery.getStatus() != PaymentRecoveryStatus.PROCESSING) {
            log.info("[PAYMENT] stage=RECOVERY_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={}", recovery.getId(), historyId, recovery.getStatus());
            return;
        }

        // 결제 복구에 대한 부분이 재시도 횟수가 초과 된 경우에는 관리자 확인으로 상태 값 변경
        if (recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            updateRecoveryExceedRetryCount(recovery, history, errorCode, errorMessage);
        }else{
            updateRecoveryPaymentFailure(recovery, history, errorCode, errorMessage);
        }



    }

    /**
     * 결제 조회 내역이 없는 상태에서 재시도 횟수가 초과한 경우 상태값 업데이트
     * - Payment , Order, PaymentAttempt
     *  -> status : PAYMENT_FAILED
     *  -> 예약 재고도 해제
     * - PaymentRecovery
     *  - recoveryStatus : MANUAL_REVIEW
     * - History
     *  - result : INCONCLUSIVE
     *  - completedAt 업데이트
     * @param recovery
     * @param history
     * @param errorCode
     * @param errorMessage
     */
    private void updateRecoveryExceedRetryCount(PaymentRecovery recovery, PaymentRecoveryHistory history, String errorCode, String errorMessage) {
        PaymentAttempt attempt = recovery.getPaymentAttempt();
        Payment payment = attempt.getPayment();
        // 이미 영속성 컨텍스트에 로딩된 값도 DB의 최신 값으로 갱신하며 잠근다.
        // 승인 완료 반영과 동일하게 Payment → Orders → Attempt 순서를 유지한다.
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        Orders order = payment.getOrder();
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);

        if (!payment.getId().equals(attempt.getPayment().getId())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_FINALIZE] 결제와 승인 시도가 연결되어 있지 않습니다.");
        }

        // PG 조회 동안 다른 요청이 완료·취소 등을 반영했다면 덮어쓰거나 예약을 해제하지 않는다.
        if (payment.getStatus() != PaymentStatus.UNKNOWN
                || attempt.getStatus() != PaymentAttemptStatus.UNKNOWN
                || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            String reason = "[결제/RECOVERY_FINALIZE] PG 조회 중 결제 상태가 변경되어 실패 확정을 중단했습니다.";
            recovery.updateRecoveryToManualCheck(errorCode, reason);
            history.updateHistoryExceedRetryCntNotFoundPayment(errorCode, reason);
            log.warn("[PAYMENT] stage=RECOVERY_FINALIZE event=STATE_CHANGED paymentId={} attemptId={} paymentStatus={} orderStatus={} attemptStatus={}",
                    payment.getId(), attempt.getId(), payment.getStatus(), order.getStatus(), attempt.getStatus());
            return;
        }

        log.info("[PAYMENT] stage=RECOVERY_FINALIZE event=VALIDATED orderNumber={} paymentId={} attemptId={} recoveryId={} historyId={} retryCount={} pgCode={}", order.getOrderNumber(), payment.getId(), attempt.getId(), recovery.getId(), history.getId(), recovery.getRetryCount(), errorCode);
        // 최종적으로 결제가 실패 했거나, 재시도 횟수가 증가하여 더 이상 복구에 대한 작업을 실행하지 않는 경우에는 운영자 확인으로 변경
        recovery.updateRecoveryToManualCheck(errorCode, errorMessage);
        history.updateHistoryExceedRetryCntNotFoundPayment(errorCode, errorMessage);

        // 결제 관련 Entity 모두 상태 값 업데이트 => 결제 실패
        payment.updateStatusToPaymentFailed();
        order.updateStatusToPaymentFailed();
        attempt.updateStatusToPaymentFailed(errorCode, errorMessage);

        // 기존에 있는 예약 선점 재고 해제
        inventoryService.releasedReservationQuantity(order,
                new PaymentBillingCardRequestDto(payment.getId(), order.getId(), order.getOrderNumber(), attempt.getId()));
        log.info("[PAYMENT] stage=RECOVERY_FINALIZE event=APPLIED_PENDING_COMMIT orderNumber={} paymentId={} attemptId={} recoveryId={} historyId={} paymentStatus={} recoveryStatus={}", order.getOrderNumber(), payment.getId(), attempt.getId(), recovery.getId(), history.getId(), payment.getStatus(), recovery.getStatus());


    }

    /**
     * 재시도 횟수가 최대 재시도 횟수보다 미만 일 떄 상태 값 업데이트
     * 결제 내역이 존재하지 않는 경우, 재시도 횟수가 초과하는 경우는 복구작업 마무리
     *  - Payment, Orders, PaymentAttempt
     *      status : PAYMENT_FAILED
     *  - OrderItem 예약 재고 해제
     *  -
     * - PaymentRecovery
     *     - action : PG_STATUS_CHECK
     *     -
     * - PaymentRecoveryHistory
     *  - action : PG_STATUS_CHECK
     *  - pgPaymentStatusCheck : errorCode
     *  - errorCode : errorCode
     *  - errorMessage : errorMessage
     * @param recovery
     * @param history
     */
    private void updateRecoveryPaymentFailure(PaymentRecovery recovery, PaymentRecoveryHistory history, String errorCode, String errorMessage) {
        //PaymentRecovery Update
        recovery.updateRecoveryForNextRetry(errorCode, errorMessage);
        //History Update
        history.updateHistoryForNextRetry(errorCode, errorMessage);
        log.info("[PAYMENT] stage=RECOVERY_RETRY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} pgCode={}", recovery.getId(), history.getId(), recovery.getRetryCount(), recovery.getMaxRetryCount(), recovery.getStatus(), errorCode);

    }
}
