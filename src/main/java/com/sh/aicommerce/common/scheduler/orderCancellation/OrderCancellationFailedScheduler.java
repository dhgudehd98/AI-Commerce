package com.sh.aicommerce.common.scheduler.orderCancellation;

import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationFailedScheduler {

    private final OrderCancellationService cancellationService;

//    @Scheduled
    public void findOrderCancellationFailed() {

        /**
         * 실패 처리된 주문들 조회
         * - PRODUCT_RESERVATION_RELEASE_FAILED("선점 재고 예약 해제 실패"),
         * - PAYMENT_CANCEL_FAILED("PG사 결제취소 요청 실패"),
         * - PAYMENT_CANCEL_FAILED_CHECK("PG사 결제취소 요청 실패 확인 필요"),
         * - ITEM_RETURN_FAILED("상품 복귀 실패"),
         */
        List<Long> cancellationFails = cancellationService.findTargetOrderCancellationFailed();

        for (Long cancellationId : cancellationFails) {

        }
    }

}