package com.sh.aicommerce.common.scheduler.payment;

import com.sh.aicommerce.paymentCompensation.service.PaymentCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationRetryScheduler {
    private final PaymentCompensationService compensationService;

    // 마지막 updatedAt이 10분 이상 지난 PENDING 보상만 재실행
//    @Scheduled(fixedDelayString = "${payment.compensation.retry.fixed-delay-ms:60000}")
    public void retryPendingCompensations() {
        log.info("[PAYMENT] stage=COMPENSATION_RETRY_SCAN event=START");
        compensationService.retryPendingCompensations();
    }
}
