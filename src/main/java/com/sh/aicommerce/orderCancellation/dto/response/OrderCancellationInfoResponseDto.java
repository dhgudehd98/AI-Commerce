package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import java.time.LocalDateTime;

public record OrderCancellationInfoResponseDto(Long cancelId, OrderCancelStatus statusCode, String status, String reason, String failCode, String failMessage, String admin, LocalDateTime requestedAt, LocalDateTime startedAt, LocalDateTime updatedAt, LocalDateTime completedAt) {
    public OrderCancellationInfoResponseDto(OrderCancellation c) {
        this(c.getId(), c.getCancelStatus(), c.getCancelStatus().getStatus(), c.getCancelReason(), c.getFailCode(), c.getFailMessage(), c.getAdmin(), c.getRequestedAt(), c.getStartedAt(), c.getUpdatedAt(), c.getCompletedAt());
    }
}
