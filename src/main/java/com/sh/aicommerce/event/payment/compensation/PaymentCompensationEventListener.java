package com.sh.aicommerce.event.payment.compensation;

import com.sh.aicommerce.paymentCompensation.service.PaymentCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentCompensationEventListener {

    private final PaymentCompensationService compensationService;

    @Async("paymentCompensationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompensation(PaymentCompensationRequested event) {
        compensationService.compensationProcess(event.compensationId());
    }
}