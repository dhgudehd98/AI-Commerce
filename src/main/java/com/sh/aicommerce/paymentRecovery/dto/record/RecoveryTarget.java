package com.sh.aicommerce.paymentRecovery.dto.record;

import com.sh.aicommerce.entity.*;

public record RecoveryTarget(
        PaymentRecovery recovery,
        PaymentRecoveryHistory history,
        Payment payment,
        PaymentAttempt attempt,
        Orders order
) {
}
