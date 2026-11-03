package com.sh.aicommerce.orderCancellation.service;

import com.sh.aicommerce.admin.service.AdminService;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.refund.RefundStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationFailedDetailResponseRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationRetryRecord;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelDetailResponseDto;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelFailResponseDto;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelListResponseDto;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.payment.service.PaymentService;
import com.sh.aicommerce.refund.service.RefundService;
import com.sh.aicommerce.toss.dto.response.PgPaymentHistoryDetailResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
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
    private final PaymentCancellationRepository paymentCancellationRepository;


    private final AdminService adminService;
    private final OutboundService outboundService;
    private final RefundService refundService;
    private final OrderCancellationTxService cancellationTxService;

    private final TossPaymentClient client;


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
                       row.order(),
                       row.payment(),
                       row.paymentCancellation()
                ));
    }

    @Transactional
    public OrderCancelDetailResponseDto orderCancellationDetail(Long cancelId) {
        log.info("[주문 취소 요청 처리] 주문 취소 ID : {}", cancelId);
        OrderCancellationDetailRecord detailRecord = orderCancellationRepository.findByIdWithOutbound(cancelId).orElseThrow(() -> new OrderCancellationException("현재 주문 취소 정보를 확인할 수가 없습니다."));
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(detailRecord.order().getId());

        return new OrderCancelDetailResponseDto(detailRecord, orderItems, paymentCancellationRepository.findByOrderCancellationId(cancelId).orElse(null));
    }

    @Transactional
    public OrderCancelDetailResponseDto orderCancelFailedDetail(Long memberId, Long cancellationId) {
        adminService.validateAdminMemberByMemberId(memberId);

        OrderCancellationFailedDetailResponseRecord data =
                orderCancellationRepository
                        .findFailureOrderCancellationDetailById(cancellationId)
                        .orElseThrow(() -> new OrderCancellationException(
                                "[취소 실패 상세 조회] 주문 취소 정보가 없습니다."
                                        + " | 취소 ID: " + cancellationId
                        ));

        if (data.order() == null
                || data.member() == null
                || data.payment() == null
                || data.outbound() == null) {
            throw new OrderCancellationException(
                    "[취소 실패 상세 조회] 필수 연결 정보가 누락되었습니다."
                            + " | 취소 ID: " + cancellationId
            );
        }

        List<OrderItem> orderItems =
                orderItemRepository.findByOrderId(data.order().getId());

        return new OrderCancelDetailResponseDto(data, orderItems, paymentCancellationRepository.findByOrderCancellationId(cancellationId).orElse(null));
    }

    @Transactional
    public Page<OrderCancelFailResponseDto> orderCancelFailList(Long memberId,int page, int size) {
        Member admin = adminService.validateAdminMemberByMemberId(memberId);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("updatedAt"),
                        Sort.Order.desc("id")
                )
        );

        // 주문취소 요청이 실패한 상태 값은 : FAILED , MANUAL_REVIEW_REQUIRED로 변경
        List<OrderCancelStatus> statuses = List.of(
                OrderCancelStatus.FAILED,
                OrderCancelStatus.MANUAL_REVIEW_REQUIRED
        );

        return orderCancellationRepository
                .findFailedOrderCancellations(statuses,pageable)
                .map(OrderCancelFailResponseDto::new);

    }

    public PgPaymentHistoryDetailResponseDto getPaymentRequestHistory(Long memberId, Long cancellationId) {
        log.info("[PG Payment 요청 내역 조회] 주문 취소 번호 : {}", cancellationId);
        adminService.validateAdminMemberByMemberId(memberId);
        Payment payment = validateAndGetPaymentKey(cancellationId);

        //토스 요청 내역 조회
        log.info(
                "[PG 결제 조회] 조회 시작 | 결제 ID: {}",
                payment.getId()
        );

        // 실제 Toss API 연동
        try {
            TossPaymentRequestHistoryResponseDto response =
                    client.getPaymentRequestHistory(payment.getPaymentKey());

            log.info(
                    "[PG 결제 조회] 조회 성공 | 결제 ID: {}, PG 상태: {}, "
                            + "원결제 금액: {}, 취소 가능 잔액: {}",
                    payment.getId(),
                    response.getStatus(),
                    response.getTotalAmount(),
                    response.getBalanceAmount()
            );

            return new PgPaymentHistoryDetailResponseDto(response);

        } catch (TossRestClientException e) {
            log.error(
                    "[PG 결제 조회] 조회 실패 | 결제 ID: {}, 사유: {}",
                    payment.getId(),
                    e.getMessage(),
                    e
            );

            throw e;
        }
    }

    @Transactional
    public Payment validateAndGetPaymentKey(Long cancellationId) {
        OrderCancellation cancellation = validateOrderCancellationById(cancellationId);
        return cancellation.getOrder().getPayment();
    }

    /**
     * 조회된 대상 결제와 전액 취소 상태를 확인한 뒤 내부 결과를 복구한다.
     * 취소 상세·금액·잔액·거래 식별자는 TxService에서 최신 데이터로 재검증한다.
     * 복구 트랜잭션이 정상 반환한 경우에만 성공 응답을 생성한다.
     */
    private Map<String, String> validatePgResponseWithPayment(Long cancellationId, OrderCancellationRetryRecord data, TossPaymentRequestHistoryResponseDto paymentRequestHistory) {

        Orders order = data.order();
        Payment payment = data.payment();

        // 정합성 검사에 실패한 경우
        if(paymentRequestHistory == null){
            throw new TossPaymentException("[주문 취소 재처리 요청 실패] PG 요청 내역이 존재하지 않습니다.");
        }

        if(paymentRequestHistory.getPaymentKey() == null || !paymentRequestHistory.getPaymentKey().equals(payment.getPaymentKey())) {
            throw new OrderCancellationException("[주문 취소 재처리 요청 실패] PG 결제 내역과 지불 내역 값이 일치하지 않습니다.");
        }

        if(paymentRequestHistory.getOrderId() == null || !paymentRequestHistory.getOrderId().equals(order.getOrderNumber())) {
            throw new PaymentException("[주문 취소 재처리 요청 실패] 결제 시, 주문 번호에 대한 내역이 다릅니다.");
        }

        if(paymentRequestHistory.getTotalAmount() == null || !paymentRequestHistory.getTotalAmount().equals(payment.getAmount())) {
            throw new PaymentException("[주문 취소 재처리 요청 실패] 실제 결제 금액과 과거 결제 금액 내역이 다릅니다.");
        }

        if (!"CANCELED".equals(paymentRequestHistory.getStatus())) {
            throw new OrderCancellationException(
                    "[주문 취소 복구 불가] PG 전액 취소 완료가 확인되지 않았습니다. 취소 ID: " + cancellationId);
        }

        // 상세 거래 검증과 저장은 TxService에서 수행하며 정상 반환 이후 커밋이 완료된다.
        cancellationTxService.applyRecoveredUpdateStatus(cancellationId, paymentRequestHistory);
        log.info("[주문 취소 복구 완료] 취소 ID: {}, 주문 ID: {}, 결제 ID: {}",
                cancellationId, order.getId(), payment.getId());
        return Map.of(
                "result", "Y",
                "message", "PG 취소 결과를 반영하여 주문 취소 처리가 완료되었습니다. 취소 ID: " + cancellationId
        );
    }
}
