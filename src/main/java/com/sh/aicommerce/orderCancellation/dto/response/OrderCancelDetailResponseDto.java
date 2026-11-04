package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationFailedDetailResponseRecord;
import com.sh.aicommerce.refund.dto.response.OrderCancelFailRefundResponseDto;
import lombok.Getter;
import java.util.List;

@Getter
public class OrderCancelDetailResponseDto {
    // 회원 관련 상세 정보
    private OrderCancelMemberResponseDto member;
    // 주문 관련 상세 정보
    private OrderCancelOrderResponseDto order;
    // 주문 취소 관련 상세 정보
    private OrderCancellationInfoResponseDto cancellation;
    // 결제 관련 상세 정보
    private OrderCancelPaymentResponseDto payment;
    // 결제 취소 관련 상세 정보 (미시작 시 null)
    private PaymentCancellationInfoResponseDto paymentCancellation;
    // 출고 및 배송 관련 상세 정보
    private OrderCancelOutboundResponseDto outbound;
    private OrderCancelDeliveryResponseDto delivery;
    // 상품 복귀 및 재고 처리 관련 상세 정보
    private OrderCancelInventoryResponseDto inventory;
    private OrderCancelActionsResponseDto actions;
    // 기존 별도 환불 처리 내역
    private OrderCancelFailRefundResponseDto refund;

    private OrderCancelDetailResponseDto(OrderCancellation cancellation, Orders order, Member member,
            Payment payment, Outbound outbound, Delivery delivery, List<OrderItem> items,
            PaymentCancellation paymentCancellation) {
        this.member = member == null ? null : new OrderCancelMemberResponseDto(member);
        this.order = new OrderCancelOrderResponseDto(order, items);
        this.cancellation = new OrderCancellationInfoResponseDto(cancellation);
        this.payment = payment == null ? null : new OrderCancelPaymentResponseDto(payment);
        this.paymentCancellation = paymentCancellation == null ? null : new PaymentCancellationInfoResponseDto(paymentCancellation);
        this.outbound = outbound == null ? null : new OrderCancelOutboundResponseDto(outbound);
        this.delivery = delivery == null ? null : new OrderCancelDeliveryResponseDto(delivery);
        this.inventory = new OrderCancelInventoryResponseDto(cancellation);
        this.actions = OrderCancelActionsResponseDto.from(cancellation, paymentCancellation);
    }

    public OrderCancelDetailResponseDto(OrderCancellationDetailRecord data, List<OrderItem> items, PaymentCancellation pc) {
        this(data.cancellation(), data.order(), data.member(), data.payment(), data.outbound(), data.delivery(), items, pc);
    }

    public OrderCancelDetailResponseDto(OrderCancellationFailedDetailResponseRecord data, List<OrderItem> items, PaymentCancellation pc) {
        this(data.cancellation(), data.order(), data.member(), data.payment(), data.outbound(), data.delivery(), items, pc);
        Refund r = data.refund();

        // 앞서 제안한 OrderCancelFailRefundResponseDto record 기준
        this.refund = r == null ? null : new OrderCancelFailRefundResponseDto(
                r.getId(),
                r.getRefundStatus(),
                r.getRequestedAmount(),
                r.getRefundedAmount(),
                r.getFailureCode(),
                r.getFailureReason(),
                r.getAttemptCount(),
                r.getLastAttemptedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCanceledAt(),
                r.getCompletedAt()
        );
    }
}
