package com.sh.aicommerce.common.scheduler.payment;

import com.sh.aicommerce.payment.service.PaymentRecoveryService;
import com.sh.aicommerce.payment.service.PaymentRetryTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessingRetryScheduler {
    private final PaymentRetryTxService retryTxService;
    private final PaymentRecoveryService recoveryService;

    /** PROCESSING 결제 대상 조회 후 공통 복구 서비스 호출 */
//    @Scheduled
    public void processingPaymentRetry() {
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=START paymentStatus=PROCESSING");
        List<Long> targets = retryTxService.findPaymentStatusProcessing();
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=CANDIDATES paymentStatus=PROCESSING count={}", targets.size());
        targets.forEach(recoveryService::recover);
    }
}
