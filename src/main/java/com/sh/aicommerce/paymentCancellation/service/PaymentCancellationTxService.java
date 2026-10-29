package com.sh.aicommerce.paymentCancellation.service;


import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.paymentCancellation.PaymentCancellationException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.paymentCancellation.InventoryCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.orderCancellation.dto.record.CancellationRequestRecord;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationTxService {

    private final OrderCancellationRepository orderCancellationRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final TossPaymentLogRepository tossPaymentLogRepository;
    private final EntityManager entityManager;

    @Transactional
    public void validateAndPaymentCancellation(Long cancellationId) {
        OrderCancellation orderCancellation = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("주문 취소 번호에 일치하는 주문 취소 내역이 존재하지 않습니다."));
        Orders order = orderCancellation.getOrder();
        Payment payment = order.getPayment();
        PaymentAttempt attempt = attemptRepository.findPaymentAttemptByOrderNumber(order.getOrderNumber())
                .orElseThrow(() -> new PaymentAttemptException("주문 번호에 해당하는 결제 승인 요청 내역이 존재하지 않습니다."));


        if(!orderCancellation.getOrder().getId().equals(order.getId()))
            throw new OrderCancellationException("취소 요청하려는 주문번호가 일치하지 않습니다.");

        if(payment.getStatus() != PaymentStatus.PAID &&
                order.getStatus() != OrderStatus.PAID &&
                attempt.getStatus() != PaymentAttemptStatus.SUCCESS)
            throw new OrderCancellationException("결제가 완료된 내역이 아닙니다.");

        PaymentCancellation paymentCancellation = paymentCancellationRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> {
                    PaymentCancellation created = PaymentCancellation.createPaymentCancellation(payment, orderCancellation);
                    PaymentCancellation saved = paymentCancellationRepository.save(created);

                    return saved;
                });
    }

    public CancellationRequestRecord startCancellation(Long orderCancellationId) {
        OrderCancellation orderCancellation = orderCancellationRepository.findByIdForUpdate(orderCancellationId)
                .orElseThrow(() -> new OrderCancellationException("주문 취소 번호 내역이 존재하지 않습니다."));

        PaymentCancellation paymentCancellation = paymentCancellationRepository.findPaymentCancellationByOrderCancellationIdForUpdate(orderCancellation.getId())
                .orElseThrow(() -> new PaymentCancellationException("결제 취소 내역이 존재하지 않습니다."));

        if(orderCancellation.getCancelStatus() != OrderCancelStatus.CANCEL_REQUEST)
            return null;

        if(paymentCancellation.getStatus() != PaymentCancellationStatus.READY)
            return null;

        // 재고 관련 처리는 결제 취소가 완료된 후에 처리할 수 있도록 해야됨.
        if (orderCancellation.getInventoryStatus() != InventoryCancelStatus.PENDING) {
            return null;
        }

        // 주문 취소, 결제 취소 상태값 업데이트
        paymentCancellation.startPaymentCancellation();
        orderCancellation.startOrderCancellation();

        return new CancellationRequestRecord(orderCancellationId, paymentCancellation.getId(), orderCancellation.getOrder().getOrderNumber(), paymentCancellation.getIdempotencyKey(), paymentCancellation.getPayment().getPaymentKey());

    }

    @Transactional
    public void cancel(TossPaymentCancelResponseDto response, CancellationRequestRecord record) {

        PaymentCancellation paymentCancellation =
                paymentCancellationRepository
                        .findByIdForUpdate(record.paymentCancellationId())
                        .orElseThrow(() ->
                                new PaymentCancellationException(
                                        "결제 취소 요청 내역이 존재하지 않습니다."
                                )
                        );

        Payment payment = paymentCancellation.getPayment();
        Orders order = payment.getOrder();

        OrderCancellation orderCancellation =
                paymentCancellation.getOrderCancellation();


        // get(0)을 호출하기 전에 검사
        if (response == null
                || response.getCancels() == null
                || response.getCancels().isEmpty()
                || response.getCancels().get(0) == null) {
            throw new PaymentCancellationException(
                    "[결제 취소 응답 검증] 취소 상세 응답이 없습니다."
            );
        }

        // Toss 응답과 DB 데이터 비교
        validateResponse(response, payment, order, paymentCancellation, orderCancellation);

        //검증 통과 후 PaymentCancellation 결제 취소 내역 업데이트
        // OrderCancellation을 취소 상태로 변경하는 것은 여기서 하는 것이 아니라 상품 복귀 확인, 선점 재고 예약 해제가 되면 FAILED로 변경
        paymentCancellation.cancelSuccess(response.getCancels().get(0));
        payment.updateCancelStatus();

        // 결제 취소 내역 후, 결제 취소 내역 TossPaymentLog에 저장
        TossPaymentLog tossPaymentLog = TossPaymentLog.createStatusToCancelSuccess(payment, response, paymentCancellation.getId());
        tossPaymentLogRepository.save(tossPaymentLog);
    }

    private void validateResponse(
            TossPaymentCancelResponseDto response,
            Payment payment,
            Orders order,
            PaymentCancellation paymentCancellation,
            OrderCancellation orderCancellation
    ) {

        // 1. PaymentCancellation 상태 검증
        if (paymentCancellation.getStatus()
                != PaymentCancellationStatus.PROCESSING) {
            throw new PaymentCancellationException(
                    "결제 취소 요청이 진행 중인 상태가 아닙니다."
            );
        }

        // 2. OrderCancellation 상태 검증
        if (orderCancellation.getCancelStatus()
                != OrderCancelStatus.PROCESSING_START) {
            throw new PaymentCancellationException(
                    "주문 취소가 진행 중인 상태가 아닙니다."
            );
        }

        // 3. Payment 상태 검증
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentException(
                    "결제 완료 상태가 아닙니다."
            );
        }

        // 4. Orders 상태 검증
        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderException(
                    "주문이 결제 완료 상태가 아닙니다."
            );
        }

        // 5. Toss 응답 검증
        if (response == null) {
            throw new PaymentException(
                    "Toss 결제 취소 응답이 존재하지 않습니다."
            );
        }

        // 6. paymentKey 비교
        if (!Objects.equals(
                response.getPaymentKey(),
                payment.getPaymentKey()
        )) {
            throw new PaymentException(
                    "Toss paymentKey가 일치하지 않습니다."
            );
        }

        // 7. Toss orderId는 우리 서비스의 orderNumber와 비교
        if (!Objects.equals(
                response.getOrderId(),
                order.getOrderNumber()
        )) {
            throw new PaymentException(
                    "Toss 주문번호가 일치하지 않습니다."
            );
        }

        // 8. 전체 취소 상태 확인
        if (!"CANCELED".equals(response.getStatus())) {
            throw new PaymentException(
                    "Toss 결제 상태가 CANCELED가 아닙니다."
            );
        }

        // 9. 원래 결제 금액 비교
        if (!Objects.equals(
                response.getTotalAmount(),
                payment.getAmount()
        )) {
            throw new PaymentException(
                    "Toss 원결제 금액이 DB 결제 금액과 일치하지 않습니다."
            );
        }

        // 10. 취소 내역 존재 여부
        List<CancelResponseDto> cancels = response.getCancels();

        if (cancels == null || cancels.isEmpty()) {
            throw new PaymentException(
                    "Toss 결제 취소 내역이 존재하지 않습니다."
            );
        }

        // 11. 취소 금액 및 취소 완료 여부
        long totalCanceledAmount = 0L;

        for (CancelResponseDto cancel : cancels) {

            if (cancel == null) {
                throw new PaymentException(
                        "Toss 취소 상세 내역이 비어 있습니다."
                );
            }

            if (!"DONE".equals(cancel.getCancelStatus())) {
                throw new PaymentException(
                        "완료되지 않은 결제 취소 내역이 존재합니다."
                );
            }

            if (cancel.getCancelAmount() == null
                    || cancel.getCancelAmount() <= 0) {
                throw new PaymentException(
                        "유효하지 않은 결제 취소 금액입니다."
                );
            }

            totalCanceledAmount = Math.addExact(
                    totalCanceledAmount,
                    cancel.getCancelAmount()
            );
        }

        // 12. 전체 취소 금액 검증
        if (totalCanceledAmount != payment.getAmount()) {
            throw new PaymentException(
                    "전체 취소 금액과 원결제 금액이 일치하지 않습니다."
            );
        }

        // 13. PaymentCancellation 요청 금액 검증
        if (!Objects.equals(
                paymentCancellation.getCancelAmount(),
                payment.getAmount()
        )) {
            throw new PaymentCancellationException(
                    "결제 취소 요청 금액과 원결제 금액이 일치하지 않습니다."
            );
        }
    }
}