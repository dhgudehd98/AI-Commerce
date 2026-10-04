package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.refund.RefundStatus;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.refund.service.RefundService;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final TossPaymentLogRepository logRepository;
    private final CardRepository cardRepository;
    private final AuthRepository authRepository;
    private final OrderCancellationRepository cancellationRepository;

    private final RefundService refundService;
    private final OutboundService outboundService;
    private final OrderCancellationService cancellationService;


    @Transactional
    public void validatePaymentByTossWidget(TossPaymentRequestDto paymentDto) {
        /**
         * 결제 승인을 하기 위한 정합성 검증
         * Payment
         * - amount = dto.getAmount(Toss Payment에서 실제 결제 준비된 금액)
         * - status = 'READY'
         * - paymentMethod = 'GENERAL'
         * - general_payment = 'TOSS'
         * Order
         * - orderNumber = paymentDto.getOrderId(PK의 orderId에 대한 값이 아닌 주문번호(OrderNumber))
         * - status = 'CREATED'
         *
         */
        log.info("[토스 페이먼츠 Payment 검증] : 주문번호(orderId) : {}", paymentDto.getOrderId());
        Integer amount = paymentDto.getAmount(); // Tosss Payment를 통한 결제 금액
        Payment payment = paymentRepository.findPaymentForUpdatePaymentStatusForUpdateInWidget(paymentDto.getAmount(), paymentDto.getOrderId()).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));

        // 금액에 대한 부분 한번 더 검증
        if(!amount.equals(payment.getAmount())) throw new PaymentException("실제 결제 금액과 저장되어 있는 결제 금액이 일치하지 않습니다.");

        // 검증 통과하면 Status에 대한 부분 중복 방지 및 멱등성 처리를 위해 PaymentStatus를 CONFIRMING으로 변경
        payment.setStatusConfirmingInWidget(paymentDto.getPaymentKey());
        log.info("[토스페이먼츠 결제 검증 통과 및 결제 상태 CONFIRM 변경] : 주문번호(orderId) : {}", paymentDto.getOrderId());
    }

    @Transactional
    public void applyPaymentByTossWidget(TossPaymentSuccessResponseDto response, TossPaymentRequestDto request) {

        log.info("[토스 페이먼츠] 결제 승인 완료 TossPaymentLog 내역 생성 및 Payment, Orders에 대한 내역 업데이트 시작");
        Payment payment = paymentRepository.findConfirmingPaymentWithOrderByOrderNumberForUpdateInWidget(request.getAmount(), request.getOrderId()).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));
        Orders order = payment.getOrder();

        TossPaymentLog tossPaymentLog = TossPaymentLog.confirmSuccess(payment, response, 200, request.toString(), response.toString());
        logRepository.save(tossPaymentLog);

        // 카드로 결제한 경우에 -> 결제내역 Payment에 대한 부분 카드 내역으로 update
        if (response.getCard() != null) {
            payment.updateCardPaymentByTossWidget(request, response);
        }
        // 간단결제(카카오페이 , 네이버페이)로 결제 한 경우에 -> 결제내역 Payment에 대한 부분 해당 결제 내역으로 update
        else if (response.getEasyPay() != null) {
            payment.updateEasyPaymentByTossWidget(request, response);
        }else{
            throw new PaymentException("지원하지 않는 토스 결제 응답입니다.");
        }
        // Orders.Status = "CREATED" -> "PAID"로 변경
        order.updateStatusToPaid();

        log.info("[토스 페이먼츠] Payment 및 Order 업데이트 완료 업데이트 정보 paymentId : {} , orderId : {}",payment.getId(), order.getId());
    }

    @Transactional
    public Payment validatePaymentByBillingCard(String orderNumber, Long memberId) {
        Payment payment = paymentRepository.findPaymentForUpdatePaymentStatusCardForUpdateInBillingCard(orderNumber, memberId).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));
        payment.setStatusConfirmingInBillingCard();

        return payment;
    }

    @Transactional
    public void applyPaymentByTossBillingCard(Long memberId, String orderNumber,TossPaymentSuccessResponseDto response) {
        Payment payment = paymentRepository.findConfirmingPaymentForUpdatePaymentStatusCardForUpdateInBillingCard(orderNumber, memberId).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));
        Orders order = payment.getOrder();
        payment.updateCardByBillingCard(response);

        if(payment.getStatus().equals(PaymentStatus.PAID)) order.updateStatusToPaid();

        // 결제 완료 시, 출고 요청 처리
        outboundService.createOutbound(order);

    }



    @Transactional(readOnly = true)
    public Member validateMember(Long memberId) {
        return authRepository.findById(memberId).orElseThrow(() -> new MemberException("존재하지 않는 회원입니다."));
    }

    @Transactional(readOnly = true)
    public Card validateCard(Long savedCardId, Long memberId) {
        return cardRepository.findByIdAndMemberId(savedCardId, memberId).orElseThrow(() -> new CardException("현재 저장되어 있는 카드 정보가 없습니다."));
    }

    @Transactional(readOnly = true)
    public boolean isPaidBillingCardPayment(String orderNumber) {
        return paymentRepository.existsPaidBillingCardPayment(orderNumber);
    }

    @Transactional(readOnly = true)
    public Payment findPaidBillingCardPayment(String orderNumber) {
        return paymentRepository.findPaidBillingCardPayment(orderNumber).orElseThrow(() -> new PaymentException("이미 결제된 주문정보가 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean isPaidTossWidgetPayment(String orderNumber, String paymentKey, Integer amount) {
        return paymentRepository.existsPaidTossWidget(orderNumber, paymentKey, amount);
    }

    @Transactional(readOnly = true)
    public Payment findPaidTossWidgetPayment(String orderNumber, String paymentKey, Integer amount) {
        return paymentRepository.findPaidTossWidgetPayment(orderNumber, paymentKey, amount).orElseThrow(() -> new PaymentException("이미 결제된 주문정보가 존재하지 않습니다."));
    }

    @Transactional
    public void payFailOrExpiredPayment(Long paymentId, LocalDateTime expiredAt) {
        Payment payment = paymentRepository
                .findExpiredPaymentForUpdate(paymentId, expiredAt)
                .orElse(null);

        if(payment == null) return ;

        failOrExpiredPaymentAndReleaseReservation(payment);
    }


    @Transactional
    public void payFailTossPayment(String orderNumber) {
        Payment payment = paymentRepository.findPaymentWithOrderByOrderNumber(orderNumber).orElseThrow(() -> new PaymentException("주문 번호에 해당하는 정보를 찾을 수 없습니다."));

        // 토스페이먼츠 결제 실패 + Payment 만료된 데이터 실패 공통 로직으로 묶어서 분리
        failOrExpiredPaymentAndReleaseReservation(payment);
    }

    private void failOrExpiredPaymentAndReleaseReservation(Payment payment) {
        Orders order = payment.getOrder();

        payment.updateFailPaymentTossWidget();

        for (OrderItem item : order.getOrderItems()) {
            Long optionId = item.getProductOption().getId();

            // 현재 예약 수량이 0보다 큰 값으로 존재하는지 확인
            ProductInventory inventory = inventoryRepository.findReservedProductInventoryForUpdate(optionId).orElseThrow(() -> new ProductException("해당 ID에 일치하는 상품 재고가 존재하지 않습니다."));
            Integer quantity = 1;
            inventory.releasedReserved(quantity);
        }

        if(payment.getStatus().equals(PaymentStatus.FAILED)) order.updateStatusToFailed();

        // 결제 실패 || 만료가 진행된 경우에는 Delivery Status의 상태 값을 실패로 설정
        if(order.getDelivery() != null) order.getDelivery().paymentFailUpdateStatus();

    }

    @Transactional
    public void cancel(TossPaymentCancelResponseDto response, Long cancellationId) {
        OrderCancellation cancellation =
                cancellationService.validateOrderCancellationByIdWithLock(
                        cancellationId
                );

        Orders order = cancellation.getOrder();

        Payment payment = paymentRepository
                .findByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new PaymentException(
                        "[결제 취소 결과 반영] 취소 대상 결제 정보가 없습니다."
                ));

        Refund refund = refundService.findRefundByCancellationIdWithLock(cancellation.getId());

        OrderCancelStatus status = cancellation.getCancelStatus();


        // 이미 반영된 결과라면 상태 일치 여부 확인 후 종료
        if (status == OrderCancelStatus.PAYMENT_CANCEL_COMPLETED
                || status == OrderCancelStatus.CANCELED) {

            if (payment.getStatus() != PaymentStatus.CANCELED
                    || refund.getRefundStatus() != RefundStatus.COMPLETED) {
                log.error("[결제 취소 완료 상태 불일치] cancellationId: {}, paymentId: {}, refundId: {}, paymentStatus: {}, refundStatus: {}",
                        cancellationId, payment.getId(), refund.getId(), payment.getStatus(), refund.getRefundStatus());
                throw new RefundException(
                        "[결제 취소 완료 검증] 결제·환불 상태가 일치하지 않습니다. | 취소 ID: " + cancellationId
                );
            }

            log.info("[결제 취소 중복 반영 건너뜀] cancellationId: {}, paymentId: {}, refundId: {}",
                    cancellationId, payment.getId(), refund.getId());
            return;
        }

        if (status != OrderCancelStatus.PAYMENT_CANCEL_PROCESSING) {
            throw new OrderCancellationException(
                    "[결제 취소 결과 반영] 취소 상태가 처리 중이 아닙니다."
            );
        }

        // 신규 결과는 취소 건과 환불 건이 모두 처리 중일 때만 반영한다.
        if (refund.getRefundStatus() != RefundStatus.PROCESSING) {
            log.error("[결제 취소 반영 불가: 환불 상태] cancellationId: {}, refundId: {}, refundStatus: {}",
                    cancellationId, refund.getId(), refund.getRefundStatus());
            throw new RefundException("[결제 취소 결과 반영] 환불 상태가 처리 중이 아닙니다. | 환불 ID: " + refund.getId());
        }

        // get(0)을 호출하기 전에 검사
        if (response == null
                || response.getCancels() == null
                || response.getCancels().isEmpty()
                || response.getCancels().get(0) == null) {
            throw new PaymentException(
                    "[결제 취소 응답 검증] 취소 상세 응답이 없습니다."
            );
        }

        // 기존 메서드: 대상 결제·주문번호·취소 상태·금액 검증
        validateOrderCancelResponse(response, payment, order, refund);

        /**
         * 동일한 트랜잭션에서 함꼐 저장
         * PaymentStatus : CANCELED로 저장
         * OrderCancelStatus : PAYMENT_CANCEL_COMPLETED 저장
         * Refund : 결제 취소 요청 완료에 대한 정보와 환불 상태에 대한 값을 저장
         */
        refund.updateStatusToCompleted(response);
        payment.updateCancelStatus();
        cancellation.updateCancelStatusToCancelFailCompleted();

        TossPaymentLog tossPaymentLog = TossPaymentLog.cancelSuccess(payment, response, cancellationId);
        logRepository.save(tossPaymentLog);

        log.info(
                "[결제 취소 반영 완료] cancellationId: {}, paymentId: {}",
                cancellationId,
                payment.getId()
        );
    }

    /**
     * PG 응답을 응답 구조 → 대상 결제 → 취소 거래 순서로 검증한다.
     * 검증 실패는 PG의 명확한 거절이 아니므로 PaymentException을 던져
     * TossAPIService에서 결과 확인 필요(FAILED_CHECK) 경로로 처리한다.
     */
    private void validateOrderCancelResponse(TossPaymentCancelResponseDto response, Payment payment, Orders order, Refund refund) {
        // 목록을 안전하게 검사한 뒤 검증할 취소 거래를 가져온다.
        CancelResponseDto cancel = requireSingleCancel(response, order.getId(), payment.getId());
        // 요청한 주문·결제가 맞고 전액 취소 상태인지 확인한다.
        validateCanceledPayment(response, payment, order);
        // 실제 취소 거래의 금액과 완료 정보를 확인한다.
        validateCancelTransaction(cancel, response.getLastTransactionKey(), payment, order.getId());

        validateRefund(response, payment, refund);
    }

    private void validateRefund(TossPaymentCancelResponseDto response, Payment payment, Refund refund) {
        if (!Objects.equals(refund.getPayment().getId(), payment.getId())
                || refund.getRequestedAmount() == null
                || !Objects.equals(
                refund.getRequestedAmount(),
                response.getCancels().get(0).getCancelAmount()
        )) {
            throw new RefundException(
                    "[환불 응답 검증] 대상 결제 또는 요청 금액이 PG 취소 결과와 일치하지 않습니다."
            );
        }

        if (refund.getPaymentKey() == null
                || refund.getPaymentKey().isBlank()
                || !refund.getPaymentKey().equals(response.getPaymentKey())) {
            throw new RefundException(
                    "[환불 응답 검증] PG 응답의 결제 식별자가 환불 대상과 일치하지 않습니다."
            );
        }

    }

    /** 현재 한 번의 전액 취소 정책에 맞는 취소 거래 한 건을 반환한다. */
    private CancelResponseDto requireSingleCancel(TossPaymentCancelResponseDto response, Long orderId, Long paymentId) {
        Integer cancelCount = response == null || response.getCancels() == null
                ? null : response.getCancels().size();
        log.info("[결제 취소 검증 시작: 응답 구조] orderId: {}, paymentId: {}, cancelCount: {}",
                orderId, paymentId, cancelCount);
        // null·목록 크기를 먼저 검사하여 get(0) 접근 시 예외를 방지한다.
        if (response == null || response.getCancels() == null
                || response.getCancels().size() != 1 || response.getCancels().get(0) == null) {
                    log.error("[결제 취소 검증 실패: 응답 구조] orderId: {}, paymentId: {}, responsePresent: {}, cancelCount: {}, firstCancelPresent: {}",
                    orderId, paymentId, response != null, cancelCount,
                    cancelCount != null && cancelCount > 0 && response.getCancels().get(0) != null);
            throw new PaymentException("[결제 취소 응답 검증] 유효한 취소 거래가 정확히 한 건이어야 합니다.");
        }
        log.info("[결제 취소 검증 통과: 응답 구조] orderId: {}, paymentId: {}", orderId, paymentId);
        return response.getCancels().get(0);
    }

    /** PG의 Payment 응답이 내부 결제 정보 및 전액 취소 조건과 일치하는지 검증한다. */
    private void validateCanceledPayment(TossPaymentCancelResponseDto response, Payment payment, Orders order) {
        log.info("[결제 취소 검증 시작: 결제 정보] orderId: {}, paymentId: {}, paymentAmount: {}, totalAmount: {}, balanceAmount: {}, pgStatus: {}",
                order.getId(), payment.getId(), payment.getAmount(), response.getTotalAmount(),
                response.getBalanceAmount(), response.getStatus());
        // paymentKey는 결제 식별자이며, PG의 orderId는 DB PK가 아닌 주문번호다.
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()
                || !payment.getPaymentKey().equals(response.getPaymentKey())
                || order.getOrderNumber() == null || order.getOrderNumber().isBlank()
                || !order.getOrderNumber().equals(response.getOrderId())) {
            log.error("[결제 취소 검증 실패: 대상 불일치] orderId: {}, paymentId: {}, paymentKeyMatches: {}, orderNumberMatches: {}",
                    order.getId(), payment.getId(),
                    payment.getPaymentKey() != null && !payment.getPaymentKey().isBlank()
                            && payment.getPaymentKey().equals(response.getPaymentKey()),
                    order.getOrderNumber() != null && !order.getOrderNumber().isBlank()
                            && order.getOrderNumber().equals(response.getOrderId()));
            throw new PaymentException("[결제 취소 대상 검증] PG 응답의 결제 식별자 또는 주문번호가 취소 대상과 다릅니다.");
        }

        // totalAmount는 최초 결제 금액이며, 전액 취소 후 balanceAmount는 0이어야 한다.
        // 금액은 내부 Payment.amount와 같아야 하고, PG 상태는 CANCELED여야 한다.
        // Objects.equals(balanceAmount, 0)는 잔액이 null인 응답도 거절한다.
        if (response.getTotalAmount() == null || response.getTotalAmount() <= 0
                || !Objects.equals(response.getTotalAmount(), payment.getAmount())
                || !Objects.equals(response.getBalanceAmount(), 0)
                || !"CANCELED".equals(response.getStatus())) {
            log.error("[결제 취소 검증 실패: 결제 금액·잔액·상태] orderId: {}, paymentId: {}, paymentAmount: {}, totalAmount: {}, balanceAmount: {}, pgStatus: {}",
                    order.getId(), payment.getId(), payment.getAmount(), response.getTotalAmount(),
                    response.getBalanceAmount(), response.getStatus());
            throw new PaymentException("[결제 취소 응답 검증] 결제 금액·잔액·상태가 전액 취소 조건과 일치하지 않습니다. | 결제 금액: " + payment.getAmount() + ", PG 원결제 금액: " + response.getTotalAmount() + ", 잔액: " + response.getBalanceAmount() + ", PG 상태: " + response.getStatus());
        }
        log.info("[결제 취소 검증 통과: 결제 정보] orderId: {}, paymentId: {}", order.getId(), payment.getId());
    }

    /** 실제 취소 거래가 전액 취소를 완료한 최신 거래인지 검증한다. */
    private void validateCancelTransaction(CancelResponseDto cancel, String lastTransactionKey, Payment payment, Long orderId) {
        Integer paymentAmount = payment.getAmount();
        log.info("[결제 취소 검증 시작: 취소 거래] orderId: {}, paymentId: {}, paymentAmount: {}, cancelAmount: {}, cancelStatus: {}",
                orderId, payment.getId(), paymentAmount, cancel.getCancelAmount(), cancel.getCancelStatus());
        if (cancel.getCancelAmount() == null || cancel.getCancelAmount() <= 0
                || !Objects.equals(cancel.getCancelAmount(), paymentAmount)) {
            log.error("[결제 취소 검증 실패: 취소 금액 불일치] orderId: {}, paymentId: {}, paymentAmount: {}, cancelAmount: {}",
                    orderId, payment.getId(), paymentAmount, cancel.getCancelAmount());
            throw new PaymentException("[결제 취소 금액 검증] 취소 금액이 유효하지 않거나 내부 결제 금액과 다릅니다. | 결제 ID: " + payment.getId() + ", 결제 금액: " + paymentAmount + ", 취소 금액: " + cancel.getCancelAmount());
        }
        // DONE 상태이며 실제 취소 금액(cancelAmount)이 내부 결제 금액과 같아야 한다.
        // 완료 시간이 존재하고 거래 식별자가 Payment 응답의 lastTransactionKey와 같아야 한다.
        if (!"DONE".equals(cancel.getCancelStatus())

                || cancel.getCanceledAt() == null
                || cancel.getTransactionKey() == null || cancel.getTransactionKey().isBlank()
                || !cancel.getTransactionKey().equals(lastTransactionKey)) {
            log.error("[결제 취소 검증 실패: 취소 거래 완료 정보] orderId: {}, paymentId: {}, paymentAmount: {}, cancelAmount: {}, cancelStatus: {}, canceledAt: {}, transactionKeyPresent: {}, transactionKeyMatches: {}",
                    orderId, payment.getId(), paymentAmount, cancel.getCancelAmount(), cancel.getCancelStatus(),
                    cancel.getCanceledAt(), cancel.getTransactionKey() != null && !cancel.getTransactionKey().isBlank(),
                    cancel.getTransactionKey() != null && cancel.getTransactionKey().equals(lastTransactionKey));
            throw new PaymentException("[결제 취소 거래 검증] 완료 상태·시간 또는 거래 식별자가 올바르지 않습니다.");
        }
        log.info("[결제 취소 검증 통과: 취소 거래] orderId: {}, paymentId: {}, paymentAmount: {}, cancelAmount: {}",
                orderId, payment.getId(), paymentAmount, cancel.getCancelAmount());
    }

    @Transactional
    public Payment prepareRefund(Long cancellationId) {
        log.info("[주문 취소 처리 요청 전 데이터 검증] 주문 취소 ID : {}", cancellationId);
        OrderCancellation cancellation = cancellationService.validateOrderCancellationByIdWithLock(cancellationId);
        OrderCancelStatus status = cancellation.getCancelStatus();

        if (status == OrderCancelStatus.PAYMENT_CANCEL_PROCESSING) {
            throw new OrderCancellationException(
                    "[환불 준비] 이미 결제 취소가 진행 중입니다."
            );
        }

        // 모든 취소 건에서 선점 해제 예약 완료시간 확인
        if(cancellation.getReservationReleasedAt() == null)
            throw new OrderCancellationException("[환불 준비] 재고 선점 해제 완료 시간이 없습니다.");

        // 현재 처리 단계와 관계없이, 상품 복귀가 필요한 건은 복귀 완료를 확인한다.
        if(cancellation.isItemReturnRequired() && cancellation.getItemReturnedAt() == null)
            throw new OrderCancellationException("[환불 준비] 필요한 상품 복귀가 확인되지 않았습니다.");

        Payment payment = paymentRepository
                .findByOrderIdForUpdate(cancellation.getOrder().getId())
                .orElseThrow(() -> new PaymentException("[환불 준비] 주문에 해당하는 결제 정보가 없습니다."));

        if (status != OrderCancelStatus.PRODUCT_RESERVATION_RELEASED
                && status != OrderCancelStatus.PAYMENT_CANCEL
                && status != OrderCancelStatus.PAYMENT_CANCEL_FAILED) {
            log.error("[주문 취소 처리 요청 실패] 주문 취소 상태에 따른 요청 실패 주문 취소 ID : {} , 현재 상태: {}", cancellationId, cancellation.getCancelStatus().getStatus());
            throw new OrderCancellationException("[환불 준비] 결제 취소 요청을 허용하지 않는 상태입니다. | 취소 ID: " + cancellationId + " 현재 상태: " + cancellation.getCancelStatus().getStatus());
        }

        if(payment.getStatus() != PaymentStatus.PAID)
            throw new PaymentException("[환불 준비] 원결제가 결제 완료 상태가 아닙니다.");

        // 환불 정보 생성
        Refund refund = refundService.createRefund(cancellation, payment);
        log.info("[주문 취소 처리 요청 전 환불 데이터 생성 완료] 환불 ID : {}", refund.getId());

        // 모든 상태 검증에 통과한 경우 -> 다음 로직은 TossAPI 결제 취소 요청 과정이기 때문에 해당 요청에 대한 데이터를 결제취소 요청 진행중으로 변경
        cancellation.updateCancelStatusToPaymentCancelProcessing();
        return payment;
    }
}
