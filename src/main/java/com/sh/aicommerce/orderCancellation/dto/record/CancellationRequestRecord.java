package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentAttempt;

public record CancellationRequestRecord(
        Long orderCancellationId,
        Long paymentCancellationId,
        String orderNumber,
        String idempotencyKey,
        String paymentKey
) {
}
