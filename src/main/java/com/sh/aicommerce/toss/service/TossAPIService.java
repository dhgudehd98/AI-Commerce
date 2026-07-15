package com.sh.aicommerce.toss.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.TossPaymentLog;
import com.sh.aicommerce.enums.payment.CardCompany;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class TossAPIService {

    @Value("${toss.payment.secretKey}")
    private String tossSecretKey;

    @Qualifier("tossRestTemplate")
    private final RestTemplate tossRestTemplate;
    private final ObjectMapper objectMapper;

    private final PaymentRepository paymentRepository;
    private final TossPaymentLogRepository logRepository;

    @Transactional
    public TossPaymentSuccessResponseDto tossPaymentConfirm(TossPaymentRequestDto paymentDto) {
        log.info("[토스 페이먼츠 결제 승인 요청] : 주문번호(orderId) : {}", paymentDto.getOrderId());

        // TossPayment 결제 승인 요청 하기전에 DB에 해당 Payment에 대한 값이 정상적으로 들어가있는지 || 결제 금액이 일치한지 확인
        Payment payment = validatePayment(paymentDto);
        Orders order = payment.getOrder();

        // Toss Payment 결제 승인 요청
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey +
                        ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedSecretKey);

        HttpEntity<TossPaymentRequestDto> httpEntity = new
                HttpEntity<>(paymentDto, headers);


        // Toss Payments API 응답 요청
        ResponseEntity<TossPaymentSuccessResponseDto> response =
                tossRestTemplate.exchange(
                        "https://api.tosspayments.com/v1/payments/confirm",
                        HttpMethod.POST,
                        httpEntity,
                        TossPaymentSuccessResponseDto.class
                );

        log.info("[토스 페이먼츠 결제 승인 완료] 응답 데이터 :{}", response.getBody());

        // 결제 승인이 성공적으로 완료되었다면 , DB에 저장된 Payment, Order에 대한 값 업데이트
        TossPaymentSuccessResponseDto responsePayment = response.getBody();

        TossPaymentLog log = TossPaymentLog.confirmSuccess(payment, responsePayment, response.getStatusCode().value(), paymentDto.toString(), responsePayment.toString());
        logRepository.save(log);

        // 카드로 결제한 경우에 -> 결제내역 Payment에 대한 부분 카드 내역으로 update
        if (responsePayment.getCard() != null) {
            payment.updateCardPayment(paymentDto, responsePayment);
        }

        // 간단결제(카카오페이 , 네이버페이)로 결제 한 경우에 -> 결제내역 Payment에 대한 부분 해당 결제 내역으로 update
        if (responsePayment.getEasyPay() != null) {
            payment.updateEasyPayment(paymentDto, responsePayment);
        }


        // 결제 승인이 완룓
        return response.getBody();
    }



    private Payment validatePayment(TossPaymentRequestDto paymentDto) {
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
        Integer amount = paymentDto.getAmount(); // Tosss Payment를 통한 결제 금액
        Payment payment = paymentRepository.findWithOrderByOrderNumber(paymentDto.getAmount(), paymentDto.getOrderId()).orElseThrow(() -> new PaymentException("해당 결제 정보가 존재하지 않습니다."));

        // 금액에 대한 부분 한번 더 검증
        if(!amount.equals(payment.getAmount())) throw new PaymentException("실제 결제 금액과 저장되어 있는 결제 금액이 일치하지 않습니다.");

        return payment;
    }
}