package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.entity.Payment;

public record OrderCancellationProcessCancelRecord(
        OrderCancellation cancellation,
        Outbound outbound,
        Orders orders,
        Payment payment
) {
}