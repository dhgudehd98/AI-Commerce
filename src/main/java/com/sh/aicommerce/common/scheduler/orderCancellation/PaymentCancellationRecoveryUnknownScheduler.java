package com.sh.aicommerce.common.scheduler.orderCancellation;


import com.sh.aicommerce.paymentCancellationRecovery.service.PaymentCancellationRecoveryService;
import com.sh.aicommerce.paymentCancellationRecovery.service.PaymentCancellationRecoveryTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCancellationRecoveryUnknownScheduler {

    private final PaymentCancellationRecoveryTxService recoveryTxService;
    private final PaymentCancellationRecoveryService recoveryService;

    @Scheduled
    public void unknownPaymentCancellation() {
        List<Long> targets = recoveryTxService.findUnknownPaymentCancellation();

        targets.forEach(recoveryService::recover);
    }
}