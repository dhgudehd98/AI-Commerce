package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.entity.Refund;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationFailedDetailResponseRecord;
import com.sh.aicommerce.refund.dto.response.OrderCancelFailRefundResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelDetailResponseDto {
    private Long cancelId;
    private Long orderId;
    private String orderNumber;
    private Long outboundId;
    private String outboundNumber;
    private String orderStatus;
    private String outboundStatus;
    private String cancelStatus;
    private OrderCancelStatus cancelStatusCode;
    private String cancelReason;
    private String failReason;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String admin;
    private boolean itemReturnRequired;
    private LocalDateTime itemReturnedAt;
    private LocalDateTime reservationReleasedAt;

    private OrderCancelMemberResponseDto member;
    private OrderCancelOrderResponseDto order;
    private OrderCancelDeliveryResponseDto delivery;
    private OrderCancelPaymentResponseDto payment;
    private OrderCancelFailRefundResponseDto refund;

    public OrderCancelDetailResponseDto(OrderCancellationDetailRecord data, List<OrderItem> orderItems) {
        this.cancelId = data.cancellation().getId();
        this.orderId = data.order().getId();
        this.orderNumber = data.order().getOrderNumber();
        this.outboundId = data.outbound().getId();
        this.outboundNumber = data.outbound().getOutboundNumber();
        this.orderStatus = data.order().getStatus().getStatus();
        this.outboundStatus = data.outbound().getOutBoundStatus().getStatus();
        this.cancelStatus = data.cancellation().getCancelStatus().getStatus();
        this.cancelReason = data.cancellation().getCancelReason();
        this.failReason = data.cancellation().getFailReason();
        this.requestedAt = data.cancellation().getRequestedAt();
        this.updatedAt = data.cancellation().getUpdatedAt();
        this.completedAt = data.cancellation().getCompletedAt();
        this.admin = data.cancellation().getAdmin();
        this.itemReturnRequired = data.cancellation().isItemReturnRequired();
        this.itemReturnedAt = data.cancellation().getItemReturnedAt();
        this.reservationReleasedAt = data.cancellation().getReservationReleasedAt();
        this.member = new OrderCancelMemberResponseDto(data.member());
        this.order = new OrderCancelOrderResponseDto(data.order(), orderItems);
        this.delivery = new OrderCancelDeliveryResponseDto(data.delivery());
        this.payment = new OrderCancelPaymentResponseDto(data.payment());
    }

    public OrderCancelDetailResponseDto(OrderCancellationFailedDetailResponseRecord data, List<OrderItem> orderItems) {
        OrderCancellation cancellation = data.cancellation();

        this.cancelId = cancellation.getId();
        this.orderId = data.order().getId();
        this.orderNumber = data.order().getOrderNumber();
        this.outboundId = data.outbound().getId();
        this.outboundNumber = data.outbound().getOutboundNumber();

        this.orderStatus = data.order().getStatus().getStatus();
        this.outboundStatus = data.outbound().getOutBoundStatus().getStatus();
        this.cancelStatus = cancellation.getCancelStatus().getStatus();
        this.cancelStatusCode = data.cancellation().getCancelStatus();

        this.cancelReason = cancellation.getCancelReason();
        this.failReason = cancellation.getFailReason();
        this.admin = cancellation.getAdmin();

        this.requestedAt = cancellation.getRequestedAt();
        this.updatedAt = cancellation.getUpdatedAt();
        this.completedAt = cancellation.getCompletedAt();

        this.itemReturnRequired = cancellation.isItemReturnRequired();
        this.itemReturnedAt = cancellation.getItemReturnedAt();
        this.reservationReleasedAt = cancellation.getReservationReleasedAt();

        this.member = new OrderCancelMemberResponseDto(data.member());
        this.order = new OrderCancelOrderResponseDto(data.order(), orderItems);
        this.payment = new OrderCancelPaymentResponseDto(data.payment());

        this.delivery = data.delivery() == null
                ? null
                : new OrderCancelDeliveryResponseDto(data.delivery());

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