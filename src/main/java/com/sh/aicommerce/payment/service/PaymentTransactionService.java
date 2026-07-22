package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.TossPaymentLog;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentLogRepository logRepository;
    private final CardRepository cardRepository;
    private final AuthRepository authRepository;

    @Transactional
    public Orders validateOrder(String orderNumber) {
        return orderRepository.findByMemberIdAndOrderNumberWithPayment(orderNumber).orElseThrow(() -> new OrderException("현재 주문번호에 해당되는 주문이 존재하지 않습니다."));
    }

    @Transactional
    public void applyConfirmResult(TossPaymentSuccessResponseDto response, TossPaymentRequestDto request) {

        log.info("[토스 페이먼츠] 결제 승인 완료 TossPaymentLog 내역 생성 및 Payment, Orders에 대한 내역 업데이트 시작");
        Payment payment = paymentRepository.findConfirmingWithOrderByOrderNumberForUpdate(request.getAmount(), request.getOrderId()).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));
        Orders order = payment.getOrder();

        TossPaymentLog tossPaymentLog = TossPaymentLog.confirmSuccess(payment, response, 200, request.toString(), response.toString());
        logRepository.save(tossPaymentLog);

        // 카드로 결제한 경우에 -> 결제내역 Payment에 대한 부분 카드 내역으로 update
        if (response.getCard() != null) {
            payment.updateCardPayment(request, response);
        }
        // 간단결제(카카오페이 , 네이버페이)로 결제 한 경우에 -> 결제내역 Payment에 대한 부분 해당 결제 내역으로 update
        else if (response.getEasyPay() != null) {
            payment.updateEasyPayment(request, response);
        }else{
            throw new PaymentException("지원하지 않는 토스 결제 응답입니다.");
        }
        // Orders.Status = "CREATED" -> "PAID"로 변경
        order.updateStatus();

        log.info("[토스 페이먼츠] Payment 및 Order 업데이트 완료 업데이트 정보 paymentId : {} , orderId : {}",payment.getId(), order.getId());
    }

    @Transactional
    public void validatePayment(TossPaymentRequestDto paymentDto) {
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
        Payment payment = paymentRepository.findPaymentForUpdatePaymentStatusForUpdate(paymentDto.getAmount(), paymentDto.getOrderId()).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));

        // 금액에 대한 부분 한번 더 검증
        if(!amount.equals(payment.getAmount())) throw new PaymentException("실제 결제 금액과 저장되어 있는 결제 금액이 일치하지 않습니다.");

        // 검증 통과하면 Status에 대한 부분 중복 방지 및 멱등성 처리를 위해 PaymentStatus를 CONFIRMING으로 변경
        payment.setStatusConfirming(paymentDto.getPaymentKey());
        log.info("[토스페이먼츠 결제 검증 통과 및 결제 상태 CONFIRM 변경] : 주문번호(orderId) : {}", paymentDto.getOrderId());
    }
}