package com.sh.aicommerce.common.scheduler.orderCancellation;

import com.sh.aicommerce.paymentCancellationRecovery.service.PaymentCancellationRecoveryService;
import com.sh.aicommerce.paymentCancellationRecovery.service.PaymentCancellationRecoveryTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationRecoveryProcessingScheduler {
    private final PaymentCancellationRecoveryTxService recoveryTxService;
    private final PaymentCancellationRecoveryService recoveryService;

    /** 30분 이상 지난 PG 취소 진행 건 조회 후 공통 복구 호출 */
//    @Scheduled(fixedDelayString = "${payment.cancellation.recovery.processing.fixed-delay-ms:60000}")
    public void processingPaymentCancellation() {
        log.info("[PAYMENT_CANCEL] stage=RECOVERY_SCAN event=START paymentCancelStatus=PROCESSING");
        List<Long> targets = recoveryTxService.findStaleProcessingPaymentCancellation();
        log.info("[PAYMENT_CANCEL] stage=RECOVERY_SCAN event=CANDIDATES paymentCancelStatus=PROCESSING count={}", targets.size());
        targets.forEach(recoveryService::recover);
    }
}
