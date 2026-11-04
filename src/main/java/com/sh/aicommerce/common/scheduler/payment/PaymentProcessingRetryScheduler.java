package com.sh.aicommerce.common.scheduler.payment;

import com.sh.aicommerce.paymentCompensation.service.PaymentCompensationScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessingRetryScheduler {
    private final PaymentCompensationScanService compensationScanService;

    /** 장시간 PROCESSING의 보상 누락을 수집하며 정상 주문 승인 복구는 수행하지 않습니다. */
//    @Scheduled(fixedDelayString = "${payment.compensation.processing.fixed-delay-ms:60000}")
    public void processingPaymentRetry() {
        log.info("[PAYMENT] stage=COMPENSATION_SCAN event=START paymentStatus=PROCESSING");
        compensationScanService.collectMissingCompensations();
    }
}
