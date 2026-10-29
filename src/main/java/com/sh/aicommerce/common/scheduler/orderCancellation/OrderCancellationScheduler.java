package com.sh.aicommerce.common.scheduler.orderCancellation;

import com.sh.aicommerce.orderCancellation.service.OrderCancellationTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationScheduler {

    private final OrderCancellationTransactionService orderCancellationTxService;
//    @Scheduled
    public void processOrderCancellation() {
        log.info("[ORDER_CANCEL] stage=SCAN event=START");
        List<Long> cancellationIds = orderCancellationTxService.findTargetOrderCancellation();
        log.info("[ORDER_CANCEL] stage=SCAN event=CANDIDATES count={}", cancellationIds.size());

        if(cancellationIds.isEmpty())
            return;

        for (Long orderCancellationId : cancellationIds) {
            //주문 취소 요청이 온 데이터들을 가져오기
            try {
                orderCancellationTxService.readyToPaymentCancellation(orderCancellationId);
            } catch (RuntimeException e) {
                log.error("[ORDER_CANCEL] stage=PREPARE event=FAILED cancellationId={}", orderCancellationId, e);
            }
        }
    }
}