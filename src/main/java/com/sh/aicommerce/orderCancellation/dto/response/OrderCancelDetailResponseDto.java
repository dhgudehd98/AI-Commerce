package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
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
}