package com.sh.aicommerce.orderCancellation.dto.response;


import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationWithOutbound;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelFailResponseDto {
    private Long orderCancellationId;
    private String orderNumber;
    private String outboundNumber;
    private String cancellationStatus;
    private String failReason;
    private String admin;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;

    public OrderCancelFailResponseDto(OrderCancellationWithOutbound cancellationFails) {
        this.orderCancellationId = cancellationFails.orderCancellation().getId();
        this.orderNumber = cancellationFails.orderNumber();
        this.outboundNumber = cancellationFails.outbound().getOutboundNumber();
        this.cancellationStatus = cancellationFails.orderCancellation().getCancelStatus().getStatus();
        this.failReason = cancellationFails.orderCancellation().getFailReason();
        this.admin = cancellationFails.orderCancellation().getAdmin();
        this.requestedAt = cancellationFails.orderCancellation().getRequestedAt();
        this.updatedAt = cancellationFails.orderCancellation().getUpdatedAt();
    }
}