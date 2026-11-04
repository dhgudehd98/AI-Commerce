package com.sh.aicommerce.paymentCancellationRecovery.dto.record;

import com.sh.aicommerce.entity.*;

public record PaymentCancellationRecoveryTarget (
        PaymentCancellation cancellation,
        PaymentCancellationRecovery recovery,
        PaymentCancellationRecoveryHistory history,
        Payment payment,
        Orders order
){
}