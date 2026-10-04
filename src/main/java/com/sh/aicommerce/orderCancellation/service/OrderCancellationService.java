package com.sh.aicommerce.orderCancellation.service;

import com.sh.aicommerce.admin.service.AdminService;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.refund.RefundStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationProcessCancelRecord;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelDetailResponseDto;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelListResponseDto;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.refund.service.RefundService;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCancellationService {

    private final ProductInventoryRepository inventoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentRepository paymentRepository;
    private final AdminService adminService;
    private final OutboundService outboundService;
    private final RefundService refundService;


    @Transactional
    public Page<OrderCancelListResponseDto> getOrderCancelList(Long memberId, int page, int size) {
        Member admin = adminService.validateAdminMemberByMemberId(memberId);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return orderCancellationRepository.findOrderCancellationAll(pageable)
                .map(row -> new OrderCancelListResponseDto(
                        row.orderCancellation(),
                        row.outbound(),
                        row.orderId(),
                        row.orderNumber()
                ));
    }

    @Transactional
    public OrderCancelDetailResponseDto orderCancellationDetail(Long cancelId) {
        log.info("[주문 취소 요청 처리] 주문 취소 ID : {}", cancelId);
        OrderCancellationDetailRecord detailRecord = orderCancellationRepository.findByIdWithOutbound(cancelId).orElseThrow(() -> new OrderCancellationException("현재 주문 취소 정보를 확인할 수가 없습니다."));
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(detailRecord.order().getId());

        return new OrderCancelDetailResponseDto(detailRecord, orderItems);
    }

    @Transactional
    public Map<String, String> cancel(Long memberId, Long cancelId) {

        try {
            log.info("[주문 취소 요청] 주문 취소 ID :{}, 관리자 ID : {}", cancelId, memberId);
            Member admin = adminService.validateAdminMemberByMemberId(memberId);

            OrderCancellation cancellation = orderCancellationRepository.findByCancelIdForUpdate(cancelId).orElseThrow(() -> new OrderCancellationException("현재 존재하지 않는 주문 취소 정보입니다. 관리자에게 문의해주세요."));
            // 주문 취소 처리를 시작 할 때는 취소 상태가 CANCEL_REQUEST일 떄만 주문 취소 처리를 진행할 수 있도록 설정
            if (!cancellation.getCancelStatus().equals(OrderCancelStatus.CANCEL_REQUEST)) {
                throw new OrderCancellationException("현재 주문 취소 처리를 할 수 없는 상태입니다. 현재 상태 : " + cancellation.getCancelStatus().getStatus());
            }
            // 주문 취소 시작 시간 업데이트 및 Status : PROCESSING_START로 업데이트, 처리 담당자 업데이트
            cancellation.startOrderCancel(admin.getMemberName());
            orderCancellationRepository.save(cancellation);

            // 정상적으로 주문 취소 처리가 완료되었으면, Scheduler를 통해서 OrderCancelStatus에 대한 값이 PROCESSING_START인것만 추출해서 처리 할 수 있도록 하기
            return Map.of(
                    "result", "Y",
                    "message", "주문 처리 취소가 접수되었습니다."
            );
        } catch (Exception e) {
            log.error("주문 취소 처리 실패 : 에러 메세지 : {}", e);
            throw e;
        }
    }

    @Transactional
    public List<Long> findTargetOrderCancellation() {
        return orderCancellationRepository.findTargetOrderCancellation();
    }

    @Transactional
    public boolean releaseReservationProduct(Long cancellationId) {
        OrderCancellation orderCancellation = orderCancellationRepository
                .findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[취소 정보 조회] 주문 취소 정보가 없습니다. | 취소 ID: " + cancellationId));
        try {
            OrderCancelStatus status = orderCancellation.getCancelStatus();
            // 이미 선점이 해제된 시점이 저장이 되어 있다면 해당 데이터는 이미 선점된 데이터 -> 즉시 종료 처리
            if(orderCancellation.getReservationReleasedAt() != null) return false;

            // 주문 취소의 상태가 -> 선점 예약 해제 실패 , 취소 처리 시작 둘다 아니면 return false 처리 => 중복 방지하기 위해서
            if(status != OrderCancelStatus.PROCESSING_START &&
                    status != OrderCancelStatus.PRODUCT_RESERVATION_RELEASE_FAILED &&
                        status != OrderCancelStatus.ITEM_RETURNED) return false;

            // 상품 복귀 확인 필요 여부가 true면 상품 복귀 확인을 안해도 됨,
            if(orderCancellation.isItemReturnRequired()) return false;

            Orders order = orderCancellation.getOrder();
            List<OrderItem> orderItems = order.getOrderItems();

            for (OrderItem item : orderItems) {
                Integer reservedQuantity = item.getQuantity();
                ProductOption option = item.getProductOption();
                log.info("예약된 재고 선점 해제 상품 옵션 번호 : {}", option.getId());

                ProductInventory inventory = inventoryRepository
                        .findByProductOptionIdForUpdate(option.getId())
                        .orElseThrow(() -> new InventoryException("[재고 선점 해제] 상품 재고 정보가 없습니다. | 상품 옵션 ID: " + option.getId()));
                log.info("[예약 재고 해제 전 상품 수량 확인] 상품 번호 : {}, 선점된 재고 : {}, 이용 가능 재고 : {}", option.getId(), inventory.getReservedQuantity(), inventory.getAvailableQuantity());
                inventory.releasedQuantity(reservedQuantity);

                inventoryRepository.save(inventory);
                log.info("[예약 재고 선점 해제 완료 후 상품 수량 확인] 주문 취소 번호 : {} 상품 번호 : {}, 선점된 재고 : {}, 이용 가능 재고 : {}", cancellationId, option.getId(), inventory.getReservedQuantity(), inventory.getAvailableQuantity());

                // 재고 변경 이력 저장
            }
            // 재고 예약 선점 해제 완료 및 선점 해제 시간 설정
            orderCancellation.updateCancelStatusToReservationReleased(OrderCancelStatus.PRODUCT_RESERVATION_RELEASED);

            return true;
        } catch (Exception e) {
            log.error(
                    "[선점 재고 해제 실패] cancellationId: {}",
                    cancellationId,
                    e
            );

            if (e instanceof InventoryException) {
                throw e;
            }
            throw new OrderCancellationException(
                    "[재고 선점 해제] 내부 처리 중 오류가 발생했습니다. | 취소 ID: " + cancellationId,
                    e
            );
        }
    }

    public void cancellationUpdateStatus(Long cancellationId) {
        log.info("[주문 취소 처리 완료] 주문 취소 상태 업데이트 주문 취소 ID : {}", cancellationId);
        OrderCancellation cancellation = validateOrderCancellationByIdWithLock(cancellationId);
        cancellation.updateCancelStatusCanceled();
    }

    @Transactional
    public boolean checkCancelStatusAndPaymentStatus(Long cancellationId) {
        OrderCancellation orderCancellation = validateOrderCancellationByIdWithLock(cancellationId);
        Orders order = orderCancellation.getOrder();

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() -> new OrderCancellationException("[환불 준비 검증] 주문에 해당하는 결제 정보가 없습니다. | 주문 ID: " + order.getId() + " 주문 취소 번호 : " + cancellationId));
        PaymentStatus paymentStatus = payment.getStatus();

        if(paymentStatus == PaymentStatus.CANCELED){
            log.info("이미 결제 취소가 완료된 주문입니다. 관리자에게 문의해주세요. 주문취소 ID : " + cancellationId);
            return false;
        }

        if (orderCancellation.getCancelStatus() != OrderCancelStatus.PRODUCT_RESERVATION_RELEASED) {
            log.info("현재 상품의 재고 선점이 해제되지 않았습니다. 관리자에게 문의해주세요. 주문 취소 ID : " + cancellationId);
            return false;
        }

        if (orderCancellation.getCancelStatus().equals(OrderCancelStatus.PAYMENT_CANCEL_PROCESSING)) {
            log.info("현재 결제 취소가 진행중인 주문 번호 입니다. 주문 취소 ID : {}", cancellationId);
            return false;
        }

        return true;
    }

    @Transactional
    // 재고 선점 예약 해제 실패 상태 변경 메소드
    public void releaseReservationFailed(Long cancellationId, String failReason) {
        OrderCancellation cancellation = validateOrderCancellationByIdWithLock(cancellationId);
        OrderCancelStatus status = cancellation.getCancelStatus();
        if (cancellation.getReservationReleasedAt() != null) {
            return;
        }

        // 다른 단계로 진행한 건을 실패 상태로 덮어쓰지 않음
        if (status != OrderCancelStatus.PROCESSING_START
                && status !=
                OrderCancelStatus.PRODUCT_RESERVATION_RELEASE_FAILED
                && status != OrderCancelStatus.ITEM_RETURNED) {
            return;
        }

        cancellation.updateCancelStatusToReservationReleaseFailed(OrderCancelStatus.PRODUCT_RESERVATION_RELEASE_FAILED, failReason);
    }

    @Transactional
    public void paymentCancelFailed(Long cancellationId,String errorCode, String failReason) {

        log.info("[주문 취소 결제 취소 요청 실패] 주문 취소 번호 : {}, 에러코드 : {}, 에러 메세지 : {}", cancellationId, errorCode, failReason);
        OrderCancellation cancellation =
                validateOrderCancellationByIdWithLock(cancellationId);

        Refund refund = refundService.findRefundByCancellationIdWithLock(cancellation.getId());

        // 신규 결과는 취소 건과 환불 건이 모두 처리 중일 때만 반영한다.
        if (refund.getRefundStatus() != RefundStatus.PROCESSING) {
            log.error("[결제 취소 요청 실패 반영 불가: 환불 상태] cancellationId: {}, refundId: {}, refundStatus: {}",
                    cancellationId, refund.getId(), refund.getRefundStatus());
            throw new RefundException("[결제 취소 실패 반영] 환불 상태가 처리 중이 아닙니다. | 환불 ID: " + refund.getId());
        }

        if (cancellation.getCancelStatus()
                != OrderCancelStatus.PAYMENT_CANCEL_PROCESSING) {
            return;
        }

        cancellation.updateCancelStatusToCancelFail(
                failReason,
                OrderCancelStatus.PAYMENT_CANCEL_FAILED
        );

        // 환불도 실패 상태로 변경
        refund.updateStatusToFailed(errorCode, failReason);

        log.warn(
                "[결제 취소 거절 반영] cancellationId: {}",
                cancellationId
        );
    }

    @Transactional
    public void paymentCancelFailedCheck(String failReason, Long cancellationId) {

        log.info("[주문 취소 결제 취소 요청 실패] 실패 사유 확인 필요, 실패 사유 : {}", failReason);
        OrderCancellation cancellation =
                validateOrderCancellationByIdWithLock(cancellationId);

        Refund refund = refundService.findRefundByCancellationIdWithLock(cancellation.getId());
        // 신규 결과는 취소 건과 환불 건이 모두 처리 중일 때만 반영한다.
        if (refund.getRefundStatus() != RefundStatus.PROCESSING) {
            log.error("[결제 취소 요청 실패 반영 불가: 환불 상태] cancellationId: {}, refundId: {}, refundStatus: {}",
                    cancellationId, refund.getId(), refund.getRefundStatus());
            throw new RefundException("[결제 취소 실패 반영] 환불 상태가 처리 중이 아닙니다. | 환불 ID: " + refund.getId());
        }

        if (cancellation.getCancelStatus()
                != OrderCancelStatus.PAYMENT_CANCEL_PROCESSING) {
            return;
        }

        cancellation.updateCancelStatusToCancelFailCheck(
                failReason,
                OrderCancelStatus.PAYMENT_CANCEL_FAILED_CHECK
        );

        refund.updateStatusToFailedCheck(failReason);

        log.warn(
                "[결제 취소 결과 확인 필요] cancellationId: {}",
                cancellationId
        );
    }

    @Transactional
    public OrderCancellation validateOrderCancellationById(Long cancellationId) {
        return orderCancellationRepository
                .findById(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[취소 정보 조회] 주문 취소 정보가 없습니다. | 취소 ID: " + cancellationId));
    }

    @Transactional
    public OrderCancellation validateOrderCancellationByIdWithLock(Long cancellationId) {
        return orderCancellationRepository
                .findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[취소 정보 조회] 주문 취소 정보가 없습니다. | 취소 ID: " + cancellationId));
    }

    @Transactional
    public List<Long> findTargetOrderCancellationFailed() {
        return orderCancellationRepository.findTargetOrderCancellationFailed();
    }

    @Transactional
    public void processItemReturn(Long cancellationId) {
        log.info("[상품 복귀 확인] 주문 취소 번호 : {}", cancellationId);
        OrderCancellation cancellation = validateOrderCancellationByIdWithLock(cancellationId);

        if(!cancellation.isItemReturnRequired())
            throw new OrderCancellationException("[상품 복귀 요청] 상품 복귀가 필요한 취소 건이 아닙니다.");

        // 상품 복귀 확인 요청으로 설정
        cancellation.updateCancelStatusToItemReturnRequest();

        // 상품 복귀 확인 완료에 대한 값은 나중에 사용자가 버튼을 클릭하면 완료되는 형식으로만 변경
    }

    @Transactional
    public void processItemReturnFailed(Long cancellationId, String failReason) {
        log.info("[상품 복귀 확인 실패] 주문 취소 번호 : {} 실패 사유 : {}", cancellationId, failReason);
        OrderCancellation cancellation = validateOrderCancellationByIdWithLock(cancellationId);

        cancellation.updateCancelStatusItemReturnFailed(failReason);
    }

    @Transactional
    public void cancelled(Long cancellationId) {
        log.info("[주문 취소 완료 처리] 주문 취소 번호 : {}", cancellationId);
        OrderCancellation cancellation = validateOrderCancellationByIdWithLock(cancellationId);
        Payment payment = paymentRepository.findByOrderId(cancellation.getOrder().getId())
                .orElseThrow(() -> new PaymentException(
                        "[주문 취소 완료 검증] 취소 대상 결제 정보가 없습니다."
                ));

        // 정상적으로 주문 취소 완료처리를 해도 되는지 확인하기 위한 검증 처리
        validateOrderCancellationToCanceled(cancellation, payment);

        cancellation.updateCancelStatusCanceled();
        outboundService.updateOutboundStatusCancel(cancellation.getCancelReason(),cancellation.getOrder().getId());

    }

    private void validateOrderCancellationToCanceled(OrderCancellation cancellation, Payment payment) {
        OrderCancelStatus cancelStatus = cancellation.getCancelStatus();
        PaymentStatus paymentStatus = payment.getStatus();

        if(cancelStatus != OrderCancelStatus.PAYMENT_CANCEL_COMPLETED || paymentStatus != PaymentStatus.CANCELED)
            throw new OrderCancellationException("[주문 취소 완료 검증] 결제 취소 완료 상태가 일치하지 않습니다. | 취소 ID: " + cancellation.getId() + ", 취소 상태: " + cancelStatus + ", 결제 상태: " + paymentStatus);

        if (cancellation.getReservationReleasedAt() == null ||
                (cancellation.isItemReturnRequired() && cancellation.getItemReturnedAt() == null)) {
            throw new OrderCancellationException("[주문 취소 완료 검증] 선점 해제 또는 필요한 상품 복귀가 완료되지 않았습니다.");
        }
    }
}
