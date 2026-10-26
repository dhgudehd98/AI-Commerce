package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.paymentRecovery.PaymentRecoveryException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
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
import com.sh.aicommerce.recovery.dto.record.RecoveryTarget;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;
import com.sh.aicommerce.enums.payment.PaymentMethod;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryResult;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRetryTxService {
    private static final int RECOVERY_ERROR_MESSAGE_MAX_LENGTH = 1000;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentRecoveryRepository recoveryRepository;
    private final PaymentRecoveryHistoryRepository recoverHistoryRepository;
    private final TossPaymentLogRepository tossPaymentLogRepository;

    private final ProductInventoryService inventoryService;
    private final OutboundService outboundService;
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

    /**
     * PG 조회 내역이 있는 경우의 흐름
     * 1. History와 Recovery 잠금 조회 후 현재 복구가 PROCESSING인지 확인한다.
     * 2. DTO 식별자, 현재 실행 회차 및 Recovery → Attempt → Payment → Orders 연결을 검증한다.
     * 3. Payment → Orders → Attempt 순으로 잠금·새로고침하여 최신 상태를 확인한다.
     *    Payment/Attempt는 UNKNOWN, Orders는 PAYMENT_PROCESSING이어야 한다.
     * 4. PG DONE 상태, 주문번호, 상품명 스냅샷, 금액, 결제키 및 카드 승인 정보를 검증한다.
     * 5. 이후 구현: 검증된 승인 정보와 완료 상태, 출고·PG 이력, Recovery·History를 함께 반영한다.
     * 현재 메서드는 검증까지만 수행하며 DB 상태 변경·출고 생성·예약 해제를 하지 않는다.
     */
    @Transactional
    public void applyPaymentRecoveryResult(Long historyId, UnknownPaymentRequestDto request, TossPaymentRequestHistoryResponseDto tossResponseDto) {
        log.info("[PAYMENT] stage=RECOVERY_APPLY event=START historyId={}", historyId);
        PaymentRecoveryHistory history = recoverHistoryRepository.findHistoryByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 복구 이력이 존재하지 않습니다."));
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(history.getPaymentRecovery().getId())
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 복구 작업이 존재하지 않습니다."));


        if (recovery.getStatus() != PaymentRecoveryStatus.PROCESSING) {
            log.info("[PAYMENT] stage=RECOVERY_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={}", recovery.getId(), historyId, recovery.getStatus());
            return;
        }

        // PG Response 데이터 검증 및 Recovery, History, Payment, Attempt, Order 데이터 검증
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=START recoveryId={} historyId={} retryCount={} maxRetryCount={}",
                recovery.getId(), historyId, recovery.getRetryCount(), recovery.getMaxRetryCount());
        RecoveryTarget target = validatePaymentRecoveryResultBeforeApply(history, recovery, request, tossResponseDto);
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=VALIDATED recoveryId={} historyId={} paymentId={} attemptId={} orderNumber={}",
                recovery.getId(), historyId, request.getPaymentId(), request.getAttemptId(), request.getOrderNumber());
        // 데이터 검증이 통과되면 Recovery, history, Payment, Attempt, Order 데이터 업데이트
        applyPaymentRecoverySuccess(tossResponseDto, target);
        // 데이터 검증이 실패하거나 , 다른 오류가 있는 경우에는 PaymentRecovery, history에 대한 내역 업데이트
    }

    private void applyPaymentRecoverySuccess(TossPaymentRequestHistoryResponseDto tossResponseDto, RecoveryTarget recoveryTarget) {
        PaymentRecovery recovery = recoveryTarget.recovery();
        PaymentRecoveryHistory history = recoveryTarget.history();
        Payment payment = recoveryTarget.payment();
        PaymentAttempt attempt = recoveryTarget.attempt();
        Orders order = recoveryTarget.order();

        log.info("[PAYMENT] stage=RECOVERY_APPLY event=STATE_UPDATE_START orderNumber={} paymentId={} attemptId={} recoveryId={} historyId={} paymentStatus={} orderStatus={} attemptStatus={}",
                order.getOrderNumber(), payment.getId(), attempt.getId(), recovery.getId(), history.getId(),
                payment.getStatus(), order.getStatus(), attempt.getStatus());
        recovery.completeRecovery();
        history.completeRecoveryHistory(tossResponseDto.getStatus());

        payment.updatePaymentByBillingCard(tossResponseDto);
        order.updateStatusToPaymentComplete();
        attempt.updateAttemptSuccessPayByRecovery(tossResponseDto);

        // 출고 내역 생성
        log.info("[PAYMENT] stage=RECOVERY_OUTBOUND event=START orderNumber={} paymentId={} recoveryId={} historyId={}",
                order.getOrderNumber(), payment.getId(), recovery.getId(), history.getId());
        outboundService.createOutbound(order);

        // PG 응답 로그 저장
        TossPaymentLog paymentLog = TossPaymentLog.confirmSuccess(payment, tossResponseDto, 200, tossResponseDto.toString());
        tossPaymentLogRepository.save(paymentLog);
        log.info("[PAYMENT] stage=RECOVERY_APPLY event=APPLIED_PENDING_COMMIT orderNumber={} paymentId={} attemptId={} recoveryId={} historyId={} paymentStatus={} orderStatus={} attemptStatus={} recoveryStatus={} historyResult={}",
                order.getOrderNumber(), payment.getId(), attempt.getId(), recovery.getId(), history.getId(),
                payment.getStatus(), order.getStatus(), attempt.getStatus(), recovery.getStatus(), history.getResult());
    }

    private RecoveryTarget validatePaymentRecoveryResultBeforeApply(
            PaymentRecoveryHistory history, PaymentRecovery recovery,
            UnknownPaymentRequestDto dto, TossPaymentRequestHistoryResponseDto response) {
        if (dto == null || response == null) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 복구 대상 또는 PG 조회 응답이 없습니다.");
        }
        if (!Objects.equals(dto.getRecoveryId(), recovery.getId())
                || !Objects.equals(history.getPaymentRecovery().getId(), recovery.getId())
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 현재 진행 중인 복구 실행 이력이 아닙니다.");
        }

        PaymentAttempt attempt = recovery.getPaymentAttempt();
        Payment payment = attempt.getPayment();
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        Orders order = payment.getOrder();
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);

        if (!Objects.equals(dto.getAttemptId(), attempt.getId())
                || !Objects.equals(dto.getPaymentId(), payment.getId())
                || !Objects.equals(dto.getOrderId(), order.getId())
                || !Objects.equals(attempt.getPayment().getId(), payment.getId())
                || !Objects.equals(dto.getOrderNumber(), order.getOrderNumber())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 복구 대상과 주문·결제·승인 시도 연결이 일치하지 않습니다.");
        }
        if (payment.getStatus() != PaymentStatus.UNKNOWN
                || attempt.getStatus() != PaymentAttemptStatus.UNKNOWN
                || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 복구 대상의 최신 상태가 결과 확인 대기 상태가 아닙니다.");
        }
        if (!"DONE".equals(response.getStatus()) || response.getApprovedAt() == null) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 승인 완료된 PG 결제 내역이 아닙니다.");
        }
        if (!Objects.equals(order.getOrderNumber(), response.getOrderId())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] PG 주문번호가 내부 주문번호와 일치하지 않습니다.");
        }
        String orderName = order.getOrderItems().stream().findFirst()
                .map(OrderItem::getProductNameSnapshot)
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 주문 상품명 스냅샷이 없습니다."));
        if (!Objects.equals(orderName, response.getOrderName())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] PG 상품명이 주문 스냅샷과 일치하지 않습니다.");
        }
        if (payment.getAmount() == null || payment.getAmount() <= 0
                || !Objects.equals(payment.getAmount(), order.getTotalPrice())
                || !Objects.equals(payment.getAmount(), attempt.getRequestAmount())
                || !Objects.equals(payment.getAmount(), response.getTotalAmount())
                || !Objects.equals(response.getTotalAmount(), response.getBalanceAmount())
                || !"KRW".equals(response.getCurrency())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 주문·승인 요청·PG 조회 금액 또는 통화가 일치하지 않습니다.");
        }
        if (!StringUtils.hasText(response.getPaymentKey())
                || (StringUtils.hasText(payment.getPaymentKey())
                    && !payment.getPaymentKey().equals(response.getPaymentKey()))
                || (StringUtils.hasText(attempt.getPaymentKey())
                    && !attempt.getPaymentKey().equals(response.getPaymentKey()))) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] PG 결제키가 없거나 기존 승인 정보와 일치하지 않습니다.");
        }
        // Billing 카드 복구 경로. PG에는 내부 savedCardId가 없으므로 직접 비교하지 않는다.
        if (payment.getPaymentMethod() != PaymentMethod.SAVED_CARD
                || payment.getSavedCardId() == null
                || response.getCard() == null
                || !StringUtils.hasText(response.getCard().getApproveNo())
                || !StringUtils.hasText(response.getCard().getIssuerCode())) {
            throw new PaymentRecoveryException("[결제/RECOVERY_VALIDATE] 저장된 카드 결제의 승인 정보가 유효하지 않습니다.");
        }

        return new RecoveryTarget(recovery, history, payment, attempt, order);
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
    public void updateRecoveryNotFoundPayment(Long historyId, String errorCode, String errorMessage) {
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
            updateRecoveryExceedRetryCountByNotFoundPayment(recovery, history, errorCode, errorMessage);
        }else{
            updateRecoveryPaymentFailureByNotFoundPayment(recovery, history, errorCode, errorMessage);
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
    private void updateRecoveryExceedRetryCountByNotFoundPayment(PaymentRecovery recovery, PaymentRecoveryHistory history, String errorCode, String errorMessage) {
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
    private void updateRecoveryPaymentFailureByNotFoundPayment(PaymentRecovery recovery, PaymentRecoveryHistory history, String errorCode, String errorMessage) {
        //PaymentRecovery Update
        recovery.updateRecoveryForNextRetry(errorCode, errorMessage);
        //History Update
        history.updateHistoryForNextRetry(errorCode, errorMessage);
        log.info("[PAYMENT] stage=RECOVERY_RETRY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} pgCode={}", recovery.getId(), history.getId(), recovery.getRetryCount(), recovery.getMaxRetryCount(), recovery.getStatus(), errorCode);
    }

    /**
     * 결제 내역이 존재하는 데이터중, 데이터 검증이나 데이터 반영시 발생하는 경우 해당 메소드에서 처리
     * - 업데이트 할 데이터 : PaymentRecovery, PaymentRecoveryHistory
     * @param historyId
     * @param e
     */
    @Transactional
    public void updateRecoveryAndHistoryFail(Long historyId, RuntimeException e) {
        log.error("[PAYMENT] stage=RECOVERY_FAILURE_APPLY event=START historyId={} errorType={}",
                historyId, e.getClass().getSimpleName(), e);
        PaymentRecoveryHistory history = recoverHistoryRepository.findHistoryByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_FAILURE_APPLY] 복구 이력이 존재하지 않습니다."));
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(history.getPaymentRecovery().getId())
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_FAILURE_APPLY] 복구 작업이 존재하지 않습니다."));

        // 완료된 작업이나 이전 회차의 실패가 현재 복구 결과를 덮어쓰지 않도록 한다.
        if (recovery.getStatus() != PaymentRecoveryStatus.PROCESSING
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            log.info("[PAYMENT] stage=RECOVERY_FAILURE_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={} attemptNumber={} retryCount={} historyResult={}",
                    recovery.getId(), historyId, recovery.getStatus(), history.getAttemptNumber(),
                    recovery.getRetryCount(), history.getResult());
            return;
        }

        String errorMessage = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
        if (errorMessage.length() > RECOVERY_ERROR_MESSAGE_MAX_LENGTH) {
            errorMessage = errorMessage.substring(0, RECOVERY_ERROR_MESSAGE_MAX_LENGTH);
        }
        // 재시도 횟수 초과 시, 예약 재고는 해제하지 않음.
        // 이미 결제가 완료된 상품이기 때문에 재시도 복구 처리 쪽에서는 예약 재고를 해제하지 않음. -> 결제 취소 요청 처리를 할 때 예약 해제하기
        if (recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            recovery.updateRecoveryToManualCheck(null, errorMessage);
        } else {
            recovery.updateRecoveryForNextRetry(null, errorMessage);
        }

        history.updateHistoryForApplyFailure(null, errorMessage);
        log.warn("[PAYMENT] stage=RECOVERY_FAILURE_APPLY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} historyResult={} errorType={}",
                recovery.getId(), historyId, recovery.getRetryCount(), recovery.getMaxRetryCount(),
                recovery.getStatus(), history.getResult(), e.getClass().getSimpleName());

    }

    /**
     * 통신 오류로 인한 실패의 경우도 내역 저장
     * 저장 항목
     * - PaymentRecovery
     * - PaymentRecoveryHistory
     * @param historyId
     * @param exception
     */
    @Transactional
    public void updatePaymentRecoveryByRestClient(Long historyId, TossRestClientException exception) {
        log.warn("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=START historyId={} errorType={}",
                historyId, exception.getClass().getSimpleName());
        PaymentRecoveryHistory history = recoverHistoryRepository.findHistoryByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_FAILURE_APPLY] 복구 이력이 존재하지 않습니다."));
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(history.getPaymentRecovery().getId())
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_FAILURE_APPLY] 복구 작업이 존재하지 않습니다."));

        if (recovery.getStatus() != PaymentRecoveryStatus.PROCESSING
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            log.info("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={} attemptNumber={} retryCount={} historyResult={}",
                    recovery.getId(), historyId, recovery.getStatus(), history.getAttemptNumber(),
                    recovery.getRetryCount(), history.getResult());
            return;
        }

        String errorMessage = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getClass().getSimpleName();
        if (errorMessage.length() > RECOVERY_ERROR_MESSAGE_MAX_LENGTH) {
            errorMessage = errorMessage.substring(0, RECOVERY_ERROR_MESSAGE_MAX_LENGTH);
        }
        // 조회 실패는 결제 거절을 뜻하지 않는다. 결제 상태와 예약 재고는 유지한다.
        // 횟수는 startPaymentRecovery에서 이미 증가했다.
        if (recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            recovery.updateRecoveryToManualCheck(null, errorMessage);
        } else {
            recovery.updateRecoveryForNextRetry(null, errorMessage);
        }
        history.updateHistoryForNextRetry(null, errorMessage);
        log.warn("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} historyResult={} errorType={}",
                recovery.getId(), historyId, recovery.getRetryCount(), recovery.getMaxRetryCount(),
                recovery.getStatus(), history.getResult(), exception.getClass().getSimpleName());
    }

    /** 일시적인 PG 조회 오류: 최대 횟수까지 재조회하며 결제 상태와 예약은 유지한다. */
    @Transactional
    public void updateRecoveryQueryFailure(Long historyId, String errorCode, String errorMessage) {
        applyRecoveryQueryFailure(historyId, errorCode, errorMessage, false);
    }

    /** 인증·설정 오류 및 분류되지 않은 조회 오류는 운영자 확인으로 전환한다. */
    @Transactional
    public void updateRecoveryForManualReview(Long historyId, TossPaymentException exception) {
        applyRecoveryQueryFailure(historyId, exception.getErrorCode(), exception.getMessage(), true);
    }

    private void applyRecoveryQueryFailure(Long historyId, String errorCode, String errorMessage,
                                           boolean manualReview) {
        log.warn("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=START historyId={} pgCode={} manualReview={}",
                historyId, errorCode, manualReview);
        PaymentRecoveryHistory history = recoverHistoryRepository.findHistoryByIdForUpdate(historyId)
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_QUERY_FAILURE_APPLY] 복구 이력이 존재하지 않습니다."));
        PaymentRecovery recovery = recoveryRepository.findRecoveryByIdForUpdate(history.getPaymentRecovery().getId())
                .orElseThrow(() -> new PaymentRecoveryException("[결제/RECOVERY_QUERY_FAILURE_APPLY] 복구 작업이 존재하지 않습니다."));
        if (recovery.getStatus() != PaymentRecoveryStatus.PROCESSING
                || history.getAttemptNumber() != recovery.getRetryCount()
                || history.getResult() != PaymentRecoveryResult.CREATE
                || history.getCompletedAt() != null) {
            log.info("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=STATE_SKIPPED recoveryId={} historyId={} recoveryStatus={}",
                    recovery.getId(), historyId, recovery.getStatus());
            return;
        }
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "PG 결제 조회에 실패했습니다.";
        if (message.length() > RECOVERY_ERROR_MESSAGE_MAX_LENGTH) {
            message = message.substring(0, RECOVERY_ERROR_MESSAGE_MAX_LENGTH);
        }
        if (manualReview || recovery.getRetryCount() >= recovery.getMaxRetryCount()) {
            recovery.updateRecoveryToManualCheck(errorCode, message);
        } else {
            recovery.updateRecoveryForNextRetry(errorCode, message);
        }
        history.updateHistoryForNextRetry(errorCode, message);
        log.warn("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=APPLIED_PENDING_COMMIT recoveryId={} historyId={} retryCount={} maxRetryCount={} recoveryStatus={} historyResult={} pgCode={}",
                recovery.getId(), historyId, recovery.getRetryCount(), recovery.getMaxRetryCount(),
                recovery.getStatus(), history.getResult(), errorCode);
    }
}
