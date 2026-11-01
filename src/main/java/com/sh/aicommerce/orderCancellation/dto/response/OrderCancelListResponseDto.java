package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelListResponseDto {
    Long orderCancellationId; // 주문 취소 번호
    String orderNumber; // 주문 번호
    Integer amount; // 결제 금액
    Integer cancelAmount; // PG 취소 작업 금액
    String orderCancelStatus; // 주문 취소 상태
    String paymentCancelStatus; // 결제 취소 상태
    String itemReturnStatus; // 상품 복귀 상태
    String inventoryStatus; // 재고 처리 상태
    String cancelReason; // 취소 사유
    LocalDateTime requestedAt; // 요청 시간
    LocalDateTime updatedAt; // 변경 시간


    public OrderCancelListResponseDto(OrderCancellation orderCancellation, Orders order, Payment payment, PaymentCancellation paymentCancellation) {
        this.orderCancellationId = orderCancellation.getId();
        this.orderNumber = order.getOrderNumber();
        this.amount = payment.getAmount();
        this.cancelAmount = paymentCancellation != null ? paymentCancellation.getCancelAmount() : 0;
        this.orderCancelStatus = orderCancellation.getCancelStatus().getStatus();
        this.paymentCancelStatus = paymentCancellation != null ? paymentCancellation.getStatus().getDescription() : "미시작";
        this.itemReturnStatus = orderCancellation.getItemReturnStatus().getDescription();
        this.inventoryStatus = orderCancellation.getInventoryStatus().getDescription();
        this.cancelReason = orderCancellation.getCancelReason();
        this.requestedAt = orderCancellation.getRequestedAt();
        this.updatedAt = orderCancellation.getUpdatedAt();
    }

}