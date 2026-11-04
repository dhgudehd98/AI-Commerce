package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.entity.PaymentCancellation;

public record OrderCancellationWithOutbound(
        OrderCancellation orderCancellation,
        PaymentCancellation paymentCancellation,
        Outbound outbound,
        Long orderId,
        String orderNumber
) {
}