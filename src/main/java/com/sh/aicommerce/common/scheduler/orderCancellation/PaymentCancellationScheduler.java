package com.sh.aicommerce.common.scheduler.orderCancellation;


import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationScheduler {

    private final OrderCancellationRepository cancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final PaymentCancellationService paymentCancellationService;

//    @Scheduled
    public void findPaymentCancellation() {
        log.info("[PAYMENT_CANCEL] stage=SCAN event=START");

        /**
         * 주문 취소 요청 처리 스케줄러
         * - 상태값 : CANCEL_REQUEST 인 주문 취소 요청 데이터 조회
         */
        List<Long> cancels = paymentCancellationRepository.findReadyPaymentCancellation();
        log.info("[PAYMENT_CANCEL] stage=SCAN event=CANDIDATES count={}", cancels.size());

        if(cancels.isEmpty())
            return;

        for (Long paymentCancellationId : cancels) {
            paymentCancellationService.process(paymentCancellationId);
        }
    }
}