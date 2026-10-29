package com.sh.aicommerce.common.scheduler.orderCancellation;


import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CancellationScheduler {

    private final OrderCancellationRepository cancellationRepository;
    private final CancellationProcessor processor;

//    @Scheduled
    public void findOrderCancellation() {

        /**
         * 주문 취소 요청 처리 스케줄러
         * - 상태값 : CANCEL_REQUEST 인 주문 취소 요청 데이터 조회
         */
        List<Long> cancels = cancellationRepository.findTargetOrderCancellation();

        if(cancels.isEmpty())
            return;

        for (Long cancelId : cancels) {
            processor.process(cancelId);
        }
    }
}