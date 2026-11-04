package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import java.time.LocalDateTime;

public record PaymentCancellationInfoResponseDto(Long paymentCancellationId, PaymentCancellationStatus statusCode, String status, Integer cancelAmount, String cancelReason, String failCode, String failMessage, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime canceledAt, LocalDateTime completedAt) {
    public PaymentCancellationInfoResponseDto(PaymentCancellation p) {
        this(p.getId(), p.getStatus(), p.getStatus().getDescription(), p.getCancelAmount(), p.getCancelReason(), p.getFailCode(), p.getFailMessage(), p.getCreatedAt(), p.getUpdatedAt(), p.getCanceledAt(), p.getCompletedAt());
    }
}
