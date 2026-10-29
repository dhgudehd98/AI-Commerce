package com.sh.aicommerce.paymentRecovery.dto.record;

import com.sh.aicommerce.entity.*;

public record PaymentRecoveryTarget(
        PaymentRecovery recovery,
        PaymentRecoveryHistory history,
        Payment payment,
        PaymentAttempt attempt,
        Orders order
) {
}
