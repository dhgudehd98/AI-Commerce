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


    private final TossTransactionService transactionService;
    private final TossPaymentClient client;

    public TossPaymentSuccessResponseDto tossPaymentConfirm(TossPaymentRequestDto request) {
        log.info("[토스 페이먼츠 결제 승인 요청] : 주문번호(orderId) : {}", request.getOrderId());

        // TossPayment 결제 승인 요청 하기전에 DB에 해당 Payment에 대한 값이 정상적으로 들어가있는지 || 결제 금액이 일치한지 확인
        transactionService.validatePayment(request);

        //Toss Payment 결제 승인 API 요청 - 실제 결제가 되는 과정은 토스 API에서 결제 승인이 이루어지는 경우 결제 승인이 됨.
        TossPaymentSuccessResponseDto response = client.confirm(request);

        // 결제가 성공적으로 완료된 경우 DB에 해당 내용 업데이트 및 Payment, Order에 대한 내역 업데이트
        transactionService.applyConfirmResult(response, request);


        // 결제 승인이 완룓
        return response;
    }
}