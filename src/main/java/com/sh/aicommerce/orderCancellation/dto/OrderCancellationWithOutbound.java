package com.sh.aicommerce.orderCancellation.dto;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Outbound;

public record OrderCancellationWithOutbound(
        OrderCancellation orderCancellation,
        Outbound outbound,
        Long orderId,
        String orderNumber
) {
}