package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import java.time.LocalDateTime;

public record OrderCancelOutboundResponseDto(Long outboundId, String outboundNumber, OutboundStatus statusCode, String status) {
    public OrderCancelOutboundResponseDto(Outbound o) {
        this(o.getId(), o.getOutboundNumber(), o.getOutBoundStatus(), o.getOutBoundStatus().getStatus());
    }
}
