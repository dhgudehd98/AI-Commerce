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

    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;


    private final AdminService adminService;
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


    @Transactional
    public OrderCancellation validateOrderCancellationById(Long cancellationId) {
        return orderCancellationRepository
                .findById(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[취소 정보 조회] 주문 취소 정보가 없습니다. | 취소 ID: " + cancellationId));
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
}
