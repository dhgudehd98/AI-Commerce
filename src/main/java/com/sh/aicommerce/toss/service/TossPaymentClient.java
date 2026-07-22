package com.sh.aicommerce.toss.service;


import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.toss.dto.request.TossPaymentBillingRequestDto;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentBillingResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

    @Value("${toss.payment.widget.secretKey}")
    private String tossSecretKey;

    @Value("${toss.payment.billing.secretKey}")
    private String tossBillingSecretKey;

    @Qualifier("tossRestTemplate")
    private final RestTemplate tossRestTemplate;

    public TossPaymentSuccessResponseDto confirm(TossPaymentRequestDto request) {
        log.info("[토스 페이먼츠 API 요청] 요청 주문번호 :{}", request.getOrderId());

        // Toss Payment 결제 승인 요청
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey +
                        ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedSecretKey);

        HttpEntity<TossPaymentRequestDto> httpEntity = new
                HttpEntity<>(request, headers);


        // Toss Payments API 응답 요청
        ResponseEntity<TossPaymentSuccessResponseDto> response =
                tossRestTemplate.exchange(
                        "https://api.tosspayments.com/v1/payments/confirm",
                        HttpMethod.POST,
                        httpEntity,
                        TossPaymentSuccessResponseDto.class
                );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new PaymentException("토스 결제 승인에 실패했습니다.");
        }

        log.info("[토스 페이먼츠 결제 승인 완료] 응답 데이터 :{}", response.getBody());

        // 결제 승인이 성공적으로 완료되었다면 , DB에 저장된 Payment, Order에 대한 값 업데이트
        return response.getBody();
    }

    public TossPaymentBillingResponseDto getBillingKey(TossPaymentBillingRequestDto request) {
        log.info("[토스 페이먼츠 카드 BillingKey 발급 요청] 요청키 :{}", request.getAuthKey());

        // Toss Payment 결제 승인 요청
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossBillingSecretKey +
                        ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedSecretKey);

        HttpEntity<TossPaymentBillingRequestDto> httpEntity = new
                HttpEntity<>(request, headers);


        // Toss Payments API 응답 요청
        ResponseEntity<TossPaymentBillingResponseDto> response =
                tossRestTemplate.exchange(
                        "https://api.tosspayments.com/v1/billing/authorizations/issue",
                        HttpMethod.POST,
                        httpEntity,
                        TossPaymentBillingResponseDto.class
                );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new PaymentException("토스 페이먼츠 자동 결제 BillingKey 발급에 실패하였습니다.");
        }

        log.info("[토스 페이먼츠 자동결제 BillingKey 발급 완료] 응답 데이터 :{}", response.getBody().toString());

        // 결제 승인이 성공적으로 완료되었다면 , DB에 저장된 Payment, Order에 대한 값 업데이트
        return response.getBody();
    }

    public void cardBilling(String billingKey, String customerKey, Integer amount, String orderNumber, String orderName, String customerEmail, String customerEmail1, String customerName, Integer taxFreeAmount) {
    }
}