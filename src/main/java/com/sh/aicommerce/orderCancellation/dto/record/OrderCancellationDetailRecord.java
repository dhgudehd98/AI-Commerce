package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.*;

public record OrderCancellationDetailRecord(
        OrderCancellation cancellation,
        Orders order,
        Outbound outbound,
        Member member,
        Delivery delivery,
        Payment payment
) {
}