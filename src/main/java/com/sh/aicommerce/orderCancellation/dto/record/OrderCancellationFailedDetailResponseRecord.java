package com.sh.aicommerce.orderCancellation.dto.record;

import com.sh.aicommerce.entity.*;

public record OrderCancellationFailedDetailResponseRecord(
        OrderCancellation cancellation,
        Orders order,
        Payment payment,
        Outbound outbound,
        Member member,
        Delivery delivery,
        Refund refund

) {
}
