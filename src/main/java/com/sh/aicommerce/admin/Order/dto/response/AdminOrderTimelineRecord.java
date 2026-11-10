package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.*;

public record AdminOrderTimelineRecord(
        Orders order,
        Payment payment,
        PaymentCancellation paymentCancellation,
        OrderCancellation orderCancellation
){
}