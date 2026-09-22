package com.sh.aicommerce.common.scheduler.orderCancellation;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationProcessCancelRecord;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.toss.service.TossAPIService;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationScheduler {

    private final OrderCancellationService cancellationService;
    private final OrderCancellationRepository cancellationRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;
    private final TossAPIService tossAPIService;
//    @Scheduled()
    public void processOrderCancellation() {

        /**
         * 주문 취소 요청 처리 할 데이터
         * - PROCESSING_START("취소 처리 시작") : 재고 선점 예약 해제 -> 재고 선점 예약 해제 완료 -> 결제 취소 요청
         * - PRODUCT_RESSERVATION_RELEASED("선점 재고 예약 해제 완료") : 재고 선점 예약 해제 완료 -> 결제 취소 요청
         * - PRODUCT_RESERVATION_RELEASE_FAILED("선점 재고 예약 해제 실패") : 재고 선점 예약 해제 실패 -> 재고 선점 예약 해제 요청 -> 예약 해제 완료 -> 결제 취소 요청
         * - PAYMENT_CANCEL_FAILED("PG사 결제취소 요청 실패")
         */
        List<Long> cancellationIds = cancellationService.findTargetOrderCancellation();
        // 현재 처리 할 주문 취소에 대한건이 없으면 해당 스케줄러 종료
        if(cancellationIds.size() == 0) return;

        for (Long cancelId : cancellationIds) {
            OrderCancellation cancellation = cancellationService.validateOrderCancellationById(cancelId);
            OrderCancelStatus status = cancellation.getCancelStatus();

            log.info("[주문 취소 처리 스케줄러 시작] 주문 취소 ID : {} 취소 상태 : {}", cancelId, status.getStatus());

            switch (status) {
                case PROCESSING_START, PRODUCT_RESERVATION_RELEASE_FAILED, ITEM_RETURNED ->
                        checkItemReturned(cancelId);
                case PRODUCT_RESERVATION_RELEASED ->
                        processRefundPayment(cancelId);
                case PAYMENT_CANCEL_FAILED -> {
                    // 재시도 가능한 오류인지, 재시도 시각이 됐는지 확인
                    // 조건을 충족하면 processRefundPayment(cancelId) 호출
                }
                default -> {
                    // 조회 이후 다른 실행이 상태를 변경한 경우 건너뜀
                }
            }

        }
    }

    // 상품 재고 복귀 확인 여부 체크
    public void checkItemReturned(Long cancellationId) {
        OrderCancellation cancellation = cancellationService.validateOrderCancellationById(cancellationId);
        // 상품 복귀 확인 여부가 필요로 하지 않은 경우(PENDING, ON_HOLD)의 상태로 설정되어 있는 경우에는 바로 재고 선점 예약 해제 처리
        if (!cancellation.isItemReturnRequired()
                && cancellation.getItemReturnedAt() == null) releaseReservationProduct(cancellationId);
        // 상품 복귀 확인 여부가 필요로 한 경우에는 재고 복귀 먼저 진행 후 , 예약 선점 해제 진행
        else processItemReturn(cancellationId);

    }

    // 상품 재고 복귀 처리
    public void processItemReturn(Long cancellationId) {

        // 재고 복귀처리가 필요한 경우에는 그냥 OrderCancelStatus -> ITEM_RETURN_REQUST(상품 복귀요청)으로만 변경하고 끝
        // 상품 복귀처리가 필요한 경우에는 실제로 사용자가 직접 옮기고 변경을 해야되기 떄문에 시스템 내부적으로 수행할 수 없음.
    }



    // 예약 재고 선점 해제 처리
    public void releaseReservationProduct(Long cancellationId) {
        log.info("[주문 취소 처리] 예약된 상품 선점 해제 취소 아이디 : {}", cancellationId);
        /**
         * 상품의 예약 선점 해제가 성공적으로 완료 -> 환불 취소 처리
         * - OrderCancelStatus의 상태가
         *      1. PROCESSING_START(취소 처리 시작)
         *      2. ITEM_RETURNED(상품 복귀 완료)
         *      3. PRODUCT_RELEASED_FAILED(예약 해제 실패)
         *      -> 위의 과정의 경우에만 예약 선점 해제 할 수 있도록 설정
         * - 상태에 대한 검사는 해당 메소드에서 검사하지 않고, releaseReservationInProduct 내부에서 검사
         */
        boolean released;
        try {
            released = cancellationService.releaseReservationProduct(cancellationId);
        } catch (Exception e) {
            // 예약선점 재고 실패 트랜잭션 설정
            cancellationService.releaseReservationFailed(cancellationId);
            return;
        }
        // 상품의 예약 선점 해제가 성공적으로 완료되었으면 환불 취소 처리 -> OrderCancellationStatus의 상태는 releaseReservationProduct 안에서 RELEASE_FAILED인지 PROCESSING_START인지 체크 하기 때문에
        if(released) processRefundPayment(cancellationId);
    }

    public void processRefundPayment(Long cancellationId) {
        try {
            /**
             * 결제 취소 요청 진행
             * - 결제 취소 요청을 진행 할 때 확인사항
             * 1. 예약 주문 선점이 해제 되었는지 확인하기
             */
            cancellationService.checkCancelStatusReleased(cancellationId);
            log.info("[주문 취소 처리] 결제 취소 요청 주문 취소 번호 : {}", cancellationId);
            tossAPIService.cancel(cancellationId);
        } catch (Exception e) {
            log.error("[주문 취소 처리 실패] 실패 사유 : {}", e);
        }
    }


}