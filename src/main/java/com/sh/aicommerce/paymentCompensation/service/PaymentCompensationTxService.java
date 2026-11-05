package com.sh.aicommerce.paymentCompensation.service;

import co.elastic.clients.elasticsearch.tasks.CancelResponse;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentCompensation.PaymentCompensationException;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.event.payment.compensation.PaymentCompensationRequested;
import com.sh.aicommerce.paymentLedger.service.PaymentLedgerService;
import org.springframework.context.ApplicationEventPublisher;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.*;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationResult;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.util.StringUtils;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentCompensation.record.PaymentCompensationRequestRecord;
import com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationTxService {

    private final com.sh.aicommerce.wms.inventory.service.ProductInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellations;
    private final OutboundRepository outbounds;
    private final TossPaymentLogRepository paymentLogs;
    private final EntityManager entityManager;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentCompensationRepository compensationRepository;
    private final PaymentLedgerService ledgerService;

    /**
     * 보상 작업의 영속화만 수행합니다. 외부 PG 호출·예약 해제·출고 변경은 하지 않습니다.
     * 호출부에서 기존 주문번호 분산락을 획득한 뒤 호출해야 합니다.
     * 보상 실행은 별도 작업자가 담당하며 이 메서드에서 실행하지 않습니다.
     */
    @Transactional
    public Long preparePaymentCompensation(PaymentBillingCardRequestDto request) {
        if (request == null || request.getPaymentId() == null || request.getAttemptId() == null
                || request.getOrderId() == null || request.getOrderNumber() == null) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 결제·주문·승인 시도 식별자가 필요합니다.");
        }

        // 기존 결제 결과 반영과 동일하게 Payment → PaymentAttempt 순서로 잠급니다.
        Payment payment = paymentRepository.findPaymentByIdWithOrderForUpdate(
                        request.getPaymentId(), request.getOrderNumber())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 결제 내역이 없습니다."));
        PaymentAttempt attempt = attemptRepository.findPaymentAttemptByAttemptIdForUpdate(request.getAttemptId())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 승인 시도가 없습니다."));

        if (attempt.getPayment() == null || payment.getOrder() == null
                || !Objects.equals(attempt.getPayment().getId(), payment.getId())
                || !Objects.equals(payment.getOrder().getId(), request.getOrderId())
                || !Objects.equals(payment.getOrder().getOrderNumber(), request.getOrderNumber())
                || !Objects.equals(attempt.getOrderNumber(), request.getOrderNumber())
                || !Objects.equals(attempt.getRequestAmount(), payment.getAmount())
                || !Objects.equals(attempt.getRequestAmount(), request.getAmount())) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 주문·결제·승인 시도의 연결 또는 금액이 일치하지 않습니다.");
        }

        return createPaymentCompensation(payment, attempt);
    }

    /** 스캔 이후 승인·거절 반영이 끝난 결제는 보상으로 전환하지 않습니다. */
    @Transactional
    public Long prepareProcessingCompensation(Long paymentId, String orderNumber, LocalDateTime cutoff) {

        Payment payment = paymentRepository.findPaymentByIdWithOrderForUpdate(paymentId, orderNumber)
                .orElseThrow(() -> invalid("결제 내역이 없습니다."));

        if (payment.getStatus() != PaymentStatus.PROCESSING ||
                payment.getUpdatedAt() == null ||
                 payment.getUpdatedAt().isAfter(cutoff)) {
            return null;
        }

        Orders order = payment.getOrder();
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_PROCESSING, OrderStatus.UNKNOWN)
                .contains(order.getStatus())) {
            throw invalid("장시간 진행 결제의 주문 상태를 확인해야 합니다.");
        }

        PaymentAttempt latest = attemptRepository.findFirstByPaymentIdOrderByIdDesc(paymentId)
                .orElseThrow(() -> invalid("보상 생성 대상 승인 시도가 없습니다."));
        PaymentAttempt attempt = attemptRepository.findPaymentAttemptByAttemptIdForUpdate(latest.getId())
                .orElseThrow(() -> invalid("보상 생성 대상 승인 시도가 없습니다."));

        if (!Objects.equals(attempt.getOrderNumber(), order.getOrderNumber())
                || !Objects.equals(attempt.getRequestAmount(), payment.getAmount())
                || !Objects.equals(payment.getAmount(), order.getTotalPrice())) {
            throw invalid("장시간 진행 결제의 주문·승인 시도·금액이 일치하지 않습니다.");
        }
        return createPaymentCompensation(payment, attempt);
    }


    private Long createPaymentCompensation(Payment payment, PaymentAttempt attempt) {

        Optional<PaymentCompensation> existing = compensationRepository.findByPaymentAttemptId(attempt.getId());

        if (existing.isPresent())
            return existing.get().getId();

        PaymentAttempt latest = attemptRepository.findFirstByPaymentIdOrderByIdDesc(payment.getId())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 최신 승인 시도가 없습니다."));

        if (!Objects.equals(latest.getId(), attempt.getId())
                || !EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN, PaymentStatus.PAID).contains(payment.getStatus())
                || !EnumSet.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.UNKNOWN,
                        PaymentAttemptStatus.SUCCESS).contains(attempt.getStatus())) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 보상 준비가 가능한 최신 승인 시도가 아닙니다.");
        }

        payment.requireCompensation();

        if (payment.getStatus() != PaymentStatus.PAID && attempt.getStatus() != PaymentAttemptStatus.SUCCESS) {
            payment.updateStatusToCompensationPending();
            attempt.updateStatusToUnknown();
        }

        PaymentCompensation saved = compensationRepository.save(PaymentCompensation.create(attempt));
        log.warn("[PAYMENT] stage=COMPENSATION_PREPARE event=SAVED_PENDING_COMMIT paymentId={} attemptId={} compensationId={}",
                payment.getId(), attempt.getId(), saved.getId());

        // 결제 실패 보상 처리 : 결제 승인 요청 내역 확인 + 결제 승인 취소 처리
        eventPublisher.publishEvent(new PaymentCompensationRequested(saved.getId()));

        return saved.getId();
    }

    /** 보상 작업을 먼저 선점하고, PG 호출용 스냅샷을 반환합니다. */
    @Transactional
    public PaymentCompensationRequestRecord findPaymentAndAttempt(Long compensationId) {
        return claimPendingCompensation(compensationId, null);
    }

    @Transactional
    public PaymentCompensationRequestRecord findPaymentAndAttemptForRetry(
            Long compensationId, java.time.LocalDateTime cutoff) {
        return claimPendingCompensation(compensationId, cutoff);
    }

    private PaymentCompensationRequestRecord claimPendingCompensation(
            Long compensationId, java.time.LocalDateTime retryCutoff) {
        PaymentCompensation compensation = lock(compensationId);

        if (compensation.getStatus() != PaymentCompensationStatus.PENDING)
            return null;

        if (retryCutoff != null) {
            if (compensation.getUpdatedAt().isAfter(retryCutoff)) return null;

            // 재시도 횟수가 초과되면 그대로 종료
            if (compensation.getRetryCount() >= PaymentCompensation.MAX_RETRY_COUNT) {
                compensation.defer("COMPENSATION_RETRY_EXHAUSTED", true);
                return null;
            }
        }

        PaymentAttempt attempt = compensation.getPaymentAttempt();
        Payment payment = attempt.getPayment();
        Orders order = payment.getOrder();

        validateForCompensation(compensation, attempt, payment, order);

        if (retryCutoff == null)
            compensation.startProcessing();
        else
            compensation.startRetryProcessing();

        return new PaymentCompensationRequestRecord(compensationId, payment.getId(), attempt.getId(),
                order.getId(), compensation.getOrderNumber(), compensation.getRequestAmount());
    }

    /** 승인 확인 후 취소 작업을 생성합니다. PG 호출 전 반드시 커밋합니다. */
    @Transactional
    public Long prepareCancellation(Long compensationId, TossPaymentRequestHistoryResponseDto response) {
        PaymentCompensation compensation = lock(compensationId);

        if (compensation.getStatus() != PaymentCompensationStatus.PROCESSING)
            return null;

        PaymentAttempt attempt = compensation.getPaymentAttempt();
        Payment payment = attempt.getPayment();
        Orders order = payment.getOrder();

        // 기존 취소 작업이 있으면 취소 결과 반영과 동일하게 OC → PC → Payment 순으로 잠급니다.
        OrderCancellation cancellation = orderCancellationRepository.findByOrderId(order.getId()).orElse(null);
        if (cancellation != null)
            entityManager.refresh(cancellation, LockModeType.PESSIMISTIC_WRITE);

        PaymentCancellation pc = cancellation == null ? null
                : paymentCancellations.findPaymentCancellationByOrderCancellationIdForUpdate(cancellation.getId()).orElse(null);

        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);

        // 데이터 검증 로직 작성
        validateForCompensation(compensation, attempt, payment, order);
        validatePgApproval(compensation, payment, attempt, order, response);

        boolean canceled = "CANCELED".equals(response.getStatus());

        // 이미 취소된 상태인지 확인 - 해당 로직은 거의 탈일은 없을거 같긴함.
        if (canceled) validatePgCancellation(response);

        if (pc != null && (!Objects.equals(pc.getPayment().getId(), payment.getId())
                || !Objects.equals(pc.getCancelAmount(), compensation.getRequestAmount()))) {
            throw invalid("기존 취소 작업의 결제 또는 금액이 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.CANCELED && !canceled
                && (pc == null || pc.getStatus() != PaymentCancellationStatus.SUCCESS)) {
            throw invalid("이미 취소된 내부 결제와 PG 조회 결과가 일치하지 않습니다.");
        }
        // 이미 반영된 성공은 과거 조회 응답으로 되돌리지 않습니다.
        if (pc != null && pc.getStatus() == PaymentCancellationStatus.SUCCESS) {
            saveCompensationLedger(payment, pc, response);
            compensation.handOffCancellation(pc);
            return null;
        }

        // 출고 관련 취소 요청으로 상태 값 업데이트
        Outbound outbound = outbounds.findOutboundByOrderIdWithLock(order.getId()).orElse(null);
        boolean itemReturnRequired = outbound != null && switch (outbound.getOutBoundStatus()) {
            case PENDING, ON_HOLD -> false;
            case PICKING, PICKED, PACKING, PACKED -> true;
            default -> throw invalid("현재 출고 단계는 자동 보상 취소 대상이 아닙니다.");
        };

        if (cancellation == null) {
            cancellation = orderCancellationRepository.save(new OrderCancellation(order,
                    "승인 결과 불명확에 따른 시스템 보상 취소", itemReturnRequired));
        } else if (cancellation.getCancelStatus() != OrderCancelStatus.PROCESSING
                && cancellation.getCancelStatus() != OrderCancelStatus.REQUESTED) {
            throw invalid("기존 주문 취소 상태를 확인해야 합니다.");
        }

        cancellation.startOrderCancellation();
        order.updateStatusToCancelRequest();

        if (outbound != null)
            outbound.updateStatusToCancelRequest();

        // 승인 사실은 기록하되 order.PAID·출고 생성 경로는 호출하지 않습니다.
        payment.recordApprovalForCompensation(response);
        attempt.updateAttemptSuccessPayByRecovery(response);

        if (pc == null)
            pc = paymentCancellations.save(PaymentCancellation.createPaymentCancellation(payment, cancellation));

        // 결제 완료 확인했으니, 취소 대기로 상태 값 변경
        compensation.handOffCancellation(pc);

        saveCompensationLedger(payment, pc, response);

        if (canceled) {
            CancelResponseDto cancel = response.getCancels().get(0);
            pc.completeCancel(cancel);
            payment.updatePaymentStatusToCanceled();
            if (paymentLogs.findLogByCancellationIdWithLock(pc.getId()).isEmpty()) {
                paymentLogs.save(TossPaymentLog.createStatusToCancelSuccess(payment, response, pc.getId()));
            }
            return null;
        }

        // READY만 기존 취소 서비스에 넘깁니다. PROCESSING·UNKNOWN은 기존 취소 복구가 담당합니다.
        return pc.getStatus() == PaymentCancellationStatus.READY ? pc.getId() : null;
    }


    /** 미조회 종료·예약 해제·보상 완료는 하나의 트랜잭션에서 반영합니다. */
    @Transactional
    public void completePaymentNotFound(Long compensationId) {
        completeUnapproved(lock(compensationId),
                PaymentCompensationResult.PAYMENT_NOT_FOUND,
                "NOT_FOUND_PAYMENT", "PG 결제 조회 내역이 없어 내부 취소 처리했습니다.");
    }

    /** 조회 객체의 승인 실패·만료를 검증한 뒤 기존 미승인 종료 경로를 사용합니다. */
    @Transactional
    public void completeUnapprovedPayment(Long compensationId, TossPaymentRequestHistoryResponseDto response) {
        PaymentCompensation compensation = lock(compensationId);

        if (compensation.getStatus() == PaymentCompensationStatus.COMPLETED)
            return;
        if (response == null || !("ABORTED".equals(response.getStatus()) || "EXPIRED".equals(response.getStatus()))
                || !Objects.equals(response.getOrderId(), compensation.getOrderNumber())
                || !Objects.equals(response.getTotalAmount(), compensation.getRequestAmount())
                || !"KRW".equals(response.getCurrency())
                || response.getApprovedAt() != null
                || response.getCancels() != null && !response.getCancels().isEmpty()) {
            throw invalid("PG 미승인 종료 상태·주문번호·금액·통화·승인 이력이 유효하지 않습니다.");
        }
        boolean aborted = "ABORTED".equals(response.getStatus());
        completeUnapproved(compensation,
                aborted ? PaymentCompensationResult.APPROVAL_ABORTED : PaymentCompensationResult.PAYMENT_EXPIRED,
                response.getStatus(), aborted ? "PG 승인 실패로 내부 취소 처리했습니다." : "PG 거래 만료로 내부 취소 처리했습니다.");
    }

    private void completeUnapproved(PaymentCompensation compensation,
            PaymentCompensationResult result,
            String reasonCode, String reasonMessage) {

        if (compensation.getStatus() == PaymentCompensationStatus.COMPLETED)
            return;

        if (compensation.getStatus() != PaymentCompensationStatus.PROCESSING) {
            throw invalid("미승인 종료 가능한 보상 상태가 아닙니다.");
        }

        PaymentAttempt attempt = compensation.getPaymentAttempt();
        Payment payment = attempt.getPayment();
        Orders order = payment.getOrder();

        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);

        validateForCompensation(compensation, attempt, payment, order);
        PaymentAttempt latest = attemptRepository.findFirstByPaymentIdOrderByIdDesc(payment.getId())
                .orElseThrow(() -> invalid("최신 승인 시도가 없습니다."));

        if (!Objects.equals(latest.getId(), attempt.getId())
                || compensation.getPaymentCancellation() != null
                || !EnumSet.of(PaymentStatus.COMPENSATION_PENDING, PaymentStatus.UNKNOWN).contains(payment.getStatus())
                || !EnumSet.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.UNKNOWN).contains(attempt.getStatus())
                || !EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_PROCESSING, OrderStatus.UNKNOWN).contains(order.getStatus())
                || StringUtils.hasText(payment.getPaymentKey()) || StringUtils.hasText(attempt.getPaymentKey())
                || orderCancellationRepository.findByOrderId(order.getId()).isPresent()
                || outbounds.findOutboundByOrderIdWithLock(order.getId()).isPresent()) {
            throw invalid("이미 승인·취소·출고가 반영되었거나 최신 시도가 아니므로 미승인 종료할 수 없습니다.");
        }

        // PG 취소 요청 없이 내부 취소와 예약 해제를 함께 반영합니다.
        inventoryService.releaseCompensationReservation(order);

        payment.updatePaymentStatusToCanceled();
        attempt.cancelUnapproved(reasonCode, reasonMessage);
        order.updateStatusToCancel();
        compensation.completeUnapproved(result, reasonCode);

        log.info("[PAYMENT] stage=COMPENSATION event=UNAPPROVED_COMPLETED_PENDING_COMMIT compensationId={} paymentId={} attemptId={} orderNumber={} reasonCode={}",
                compensation.getId(), payment.getId(), attempt.getId(), order.getOrderNumber(), reasonCode);
    }

    @Transactional
    public void defer(Long compensationId, String errorCode, boolean manualReview) {
        lock(compensationId).defer(errorCode, manualReview);
    }

    private PaymentCompensation lock(Long compensationId) {
        return compensationRepository.findByIdForUpdate(compensationId)
                .orElseThrow(() -> invalid("보상 작업이 없습니다."));
    }
    // PaymentCompensation에 대한 데이터 검증

    private void validateForCompensation(PaymentCompensation compensation, PaymentAttempt attempt,
                                         Payment payment, Orders order) {
        if (!payment.isCompensationRequired()
                || !Objects.equals(attempt.getPayment().getId(), payment.getId())
                || !Objects.equals(payment.getOrder().getId(), order.getId())
                || !Objects.equals(compensation.getOrderNumber(), order.getOrderNumber())
                || !Objects.equals(attempt.getOrderNumber(), order.getOrderNumber())
                || !Objects.equals(compensation.getRequestAmount(), payment.getAmount())
                || !Objects.equals(compensation.getRequestAmount(), attempt.getRequestAmount())
                || !Objects.equals(compensation.getRequestAmount(), order.getTotalPrice())) {
            throw invalid("보상 대상의 주문·결제·승인 시도 연결 또는 금액이 일치하지 않습니다.");
        }
        if (!EnumSet.of(PaymentStatus.COMPENSATION_PENDING, PaymentStatus.UNKNOWN, PaymentStatus.PAID,
                PaymentStatus.CANCELED).contains(payment.getStatus())
                || !EnumSet.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.UNKNOWN,
                PaymentAttemptStatus.SUCCESS).contains(attempt.getStatus())) {
            throw invalid("보상 처리가 가능한 상태가 아닙니다.");
        }
    }
    // PG에서 결제 승인 요청 내역과 기존 결제 내역에 대한 데이터 검증

    private void validatePgApproval(PaymentCompensation compensation, Payment payment, PaymentAttempt attempt,
                                    Orders order, TossPaymentRequestHistoryResponseDto response) {
        if (response == null || !("DONE".equals(response.getStatus()) || "CANCELED".equals(response.getStatus()))
                || !Objects.equals(response.getOrderId(), order.getOrderNumber())
                || !Objects.equals(response.getTotalAmount(), compensation.getRequestAmount())
                || !"KRW".equals(response.getCurrency()) || response.getApprovedAt() == null
                || !StringUtils.hasText(response.getPaymentKey())
                || StringUtils.hasText(payment.getPaymentKey()) && !Objects.equals(payment.getPaymentKey(), response.getPaymentKey())
                || StringUtils.hasText(attempt.getPaymentKey()) && !Objects.equals(attempt.getPaymentKey(), response.getPaymentKey())) {
            throw invalid("PG 승인 상태·주문번호·금액·통화·결제키가 유효하지 않습니다.");
        }
        if ("DONE".equals(response.getStatus())
                && (!Objects.equals(response.getTotalAmount(), response.getBalanceAmount())
                || response.getCancels() != null && !response.getCancels().isEmpty())) {
            throw invalid("전체 취소 전제와 PG 잔액·취소 내역이 일치하지 않습니다.");
        }
    }

    private void validatePgCancellation(TossPaymentRequestHistoryResponseDto response) {
        if (!Integer.valueOf(0).equals(response.getBalanceAmount())
                || response.getCancels() == null || response.getCancels().size() != 1) {
            throw invalid("전액 취소가 확인된 단일 거래가 아닙니다.");
        }
        CancelResponseDto cancel = response.getCancels().get(0);
        if (cancel == null || !"DONE".equals(cancel.getCancelStatus()) || cancel.getCanceledAt() == null
                || !StringUtils.hasText(cancel.getTransactionKey())
                || !Objects.equals(cancel.getCancelAmount(), response.getTotalAmount())
                || !Integer.valueOf(0).equals(cancel.getRefundableAmount())) {
            throw invalid("PG 취소 거래가 유효하지 않습니다.");
        }
    }

    private void saveCompensationLedger(Payment payment, PaymentCancellation cancellation,
                                        TossPaymentRequestHistoryResponseDto response) {
        PaymentLedger ledger;
        if ("CANCELED".equals(response.getStatus())) {
            CancelResponseDto cancel = response.getCancels().get(0);
            ledger = PaymentLedger.createCancellation(payment, cancellation, cancel.getCancelAmount(),
                    response.getCurrency(), response.getPaymentKey(), cancel.getTransactionKey(), cancel.getCanceledAt());
        } else {
            // DONE 조회로 확인한 승인 로그와 원장을 취소 요청 전에 함께 커밋합니다.
            if (!paymentLogs.existsByPaymentIdAndRequestTypeAndTossStatus(payment.getId(), "CONFIRM", "DONE")) {
                // 최초 승인 API의 원문 응답은 없으므로 임의의 payload를 기록하지 않습니다.
                paymentLogs.save(TossPaymentLog.confirmSuccess(payment, response, 200, null));
                log.info("[PAYMENT] stage=COMPENSATION_APPROVAL_LOG event=SAVED_PENDING_COMMIT paymentId={}",
                        payment.getId());
            }
            ledger = PaymentLedger.createApproval(payment, response.getTotalAmount(), response.getCurrency(),
                    response.getPaymentKey(), response.getLastTransactionKey(), response.getApprovedAt());
        }
        ledgerService.saveIfAbsent(ledger);
    }

    private PaymentCompensationException invalid(String message) {
        return new PaymentCompensationException("[결제/COMPENSATION] " + message);
    }
}
