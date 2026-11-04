package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.refund.service.RefundService;
import com.sh.aicommerce.toss.dto.payment.request.TossPaymentRequestDto;
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

        payment.updatePaymentStatusToProcessingByWidget(paymentDto.getPaymentKey());
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


    @Transactional(readOnly = true)
    public boolean isPaidTossWidgetPayment(String orderNumber, String paymentKey, Integer amount) {
        return paymentRepository.existsPaidTossWidget(orderNumber, paymentKey, amount);
    }

    @Transactional(readOnly = true)
    public Payment findPaidTossWidgetPayment(String orderNumber, String paymentKey, Integer amount) {
        return paymentRepository.findPaidTossWidgetPayment(orderNumber, paymentKey, amount).orElseThrow(() -> new PaymentException("이미 결제된 주문정보가 존재하지 않습니다."));
    }

//    @Transactional
//    public void payFailOrExpiredPayment(Long paymentId, LocalDateTime expiredAt) {
//        Payment payment = paymentRepository
//                .findExpiredPaymentForUpdate(paymentId, expiredAt)
//                .orElse(null);
//
//        if(payment == null) return ;
//
//        failOrExpiredPaymentAndReleaseReservation(payment);
//    }


    @Transactional
    public void payFailTossPayment(String orderNumber, String failReason) {
        Payment payment = paymentRepository.findPaymentWithOrderByOrderNumber(orderNumber).orElseThrow(() -> new PaymentException("주문 번호에 해당하는 정보를 찾을 수 없습니다."));

        // 토스페이먼츠 결제 실패 + Payment 만료된 데이터 실패 공통 로직으로 묶어서 분리
        failOrExpiredPaymentAndReleaseReservation(payment, failReason);
    }

    private void failOrExpiredPaymentAndReleaseReservation(Payment payment, String failReason) {
        Orders order = payment.getOrder();

        payment.updateStatusToRejected(failReason);

        for (OrderItem item : order.getOrderItems()) {
            Long optionId = item.getProductOption().getId();

            // 현재 예약 수량이 0보다 큰 값으로 존재하는지 확인
            ProductInventory inventory = inventoryRepository.findReservedProductInventoryForUpdate(optionId).orElseThrow(() -> new ProductException("해당 ID에 일치하는 상품 재고가 존재하지 않습니다."));
            Integer quantity = 1;
            inventory.releasedReserved(quantity);
        }

        if(payment.getStatus().equals(PaymentStatus.PAYMENT_REJECTED)) order.updateStatusToRejected();

        // 결제 실패 || 만료가 진행된 경우에는 Delivery Status의 상태 값을 실패로 설정
        if(order.getDelivery() != null) order.getDelivery().paymentFailUpdateStatus();

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

//        validateRefund(response, payment, refund);
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


}
