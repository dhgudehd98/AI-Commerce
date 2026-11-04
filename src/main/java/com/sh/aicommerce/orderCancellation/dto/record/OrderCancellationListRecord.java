package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentCancellation;

public record OrderCancellationListRecord(
        OrderCancellation orderCancellation,
        Orders order,
        Payment payment,
        PaymentCancellation paymentCancellation
) {
}
