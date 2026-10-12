package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.*;

public record OrderCancellationRetryRecord(
        OrderCancellation cancellation,
        Orders order,
        Payment payment,
        Refund refund,
        Outbound outbound
) {
}
