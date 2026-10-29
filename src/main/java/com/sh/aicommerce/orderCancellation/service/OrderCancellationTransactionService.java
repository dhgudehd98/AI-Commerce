package com.sh.aicommerce.orderCancellation.service;

import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationTransactionService {

    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Long> findTargetOrderCancellation() {
        return orderCancellationRepository.findTargetOrderCancellation();
    }

    /** 요청 잠금·검증 후 PG 취소 READY 작업 준비. 외부 API·재고 변경 없음 */
    @Transactional
    public void readyToPaymentCancellation(Long cancellationId) {
        log.info("[ORDER_CANCEL] stage=PREPARE event=START cancellationId={}", cancellationId);
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[주문 취소/PREPARE] 취소 요청 내역이 존재하지 않습니다."));

        if (cancellation.getCancelStatus() != OrderCancelStatus.REQUESTED) {
            log.info("[ORDER_CANCEL] stage=PREPARE event=STATE_SKIPPED cancellationId={} status={}",
                    cancellationId, cancellation.getCancelStatus());
            return;
        }

        Orders order = cancellation.getOrder();
        Payment payment = order.getPayment();
        if (payment == null) {
            throw new OrderCancellationException("[주문 취소/PREPARE] 주문에 연결된 결제 내역이 존재하지 않습니다.");
        }

        // 다른 취소 처리에서도 동일한 잠금 순서 유지 필요
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        PaymentAttempt attempt = attemptRepository.findPaymentAttemptByOrderNumber(order.getOrderNumber())
                .orElseThrow(() -> new PaymentAttemptException("[주문 취소/PREPARE] 결제 승인 내역이 존재하지 않습니다."));
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);

        if (!Objects.equals(payment.getOrder().getId(), order.getId())
                || attempt.getPayment() == null
                || !Objects.equals(attempt.getPayment().getId(), payment.getId())) {
            throw new OrderCancellationException("[주문 취소/PREPARE] 주문·결제·승인 내역의 연결 관계가 일치하지 않습니다.");
        }
        if (order.getStatus() != OrderStatus.PAID
                || payment.getStatus() != PaymentStatus.PAID
                || attempt.getStatus() != PaymentAttemptStatus.SUCCESS) {
            throw new OrderCancellationException("[주문 취소/PREPARE] 결제 완료된 주문만 취소 준비가 가능합니다.");
        }
        if (!StringUtils.hasText(payment.getPaymentKey())) {
            throw new OrderCancellationException("[주문 취소/PREPARE] PG 결제키가 존재하지 않습니다.");
        }

        PaymentCancellation paymentCancellation = paymentCancellationRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> paymentCancellationRepository.save(
                        PaymentCancellation.createPaymentCancellation(payment, cancellation)));
        if (!Objects.equals(paymentCancellation.getOrderCancellation().getId(), cancellationId)) {
            throw new OrderCancellationException("[주문 취소/PREPARE] 기존 PG 취소 작업의 주문 취소 요청이 일치하지 않습니다.");
        }
        if (paymentCancellation.getStatus() != PaymentCancellationStatus.READY) {
            throw new OrderCancellationException("[주문 취소/PREPARE] 기존 PG 취소 작업이 준비 상태가 아닙니다.");
        }

        cancellation.startOrderCancellation();
        log.info("[ORDER_CANCEL] stage=PREPARE event=APPLIED_PENDING_COMMIT cancellationId={} paymentCancellationId={} orderNumber={} orderCancelStatus={} paymentCancelStatus={}",
                cancellationId, paymentCancellation.getId(), order.getOrderNumber(),
                cancellation.getCancelStatus(), paymentCancellation.getStatus());
    }
}
