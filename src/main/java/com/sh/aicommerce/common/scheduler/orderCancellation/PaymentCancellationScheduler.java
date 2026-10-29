package com.sh.aicommerce.common.scheduler.orderCancellation;


import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
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
    private final CancellationProcessor processor;

//    @Scheduled
    public void findPaymentCancellation() {

        /**
         * 주문 취소 요청 처리 스케줄러
         * - 상태값 : CANCEL_REQUEST 인 주문 취소 요청 데이터 조회
         */
        List<Long> cancels = paymentCancellationRepository.findTargetPaymentCancellation();

        if(cancels.isEmpty())
            return;

        for (Long paymentCancellationId : cancels) {
            processor.process(paymentCancellationId);
        }
    }
}