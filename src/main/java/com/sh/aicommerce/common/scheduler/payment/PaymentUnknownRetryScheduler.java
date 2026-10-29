package com.sh.aicommerce.common.scheduler.payment;

import com.sh.aicommerce.payment.service.PaymentRecoveryService;
import com.sh.aicommerce.payment.service.PaymentRecoveryTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentUnknownRetryScheduler {
    private final PaymentRecoveryTxService retryTxService;
    private final PaymentRecoveryService recoveryService;

    /** UNKNOWN 결제 대상 조회 후 공통 복구 서비스 호출 */
//    @Scheduled
    public void unknownPaymentRetry() {
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=START paymentStatus=UNKNOWN");
        List<Long> targets = retryTxService.getUnknownPaymentRetry();
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=CANDIDATES paymentStatus=UNKNOWN count={}", targets.size());
        targets.forEach(recoveryService::recover);
    }
}
