package com.sh.aicommerce.toss.service;


import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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

    public ResponseEntity<?> tossPaymentConfirm(TossPaymentRequestDto paymentDto) {
        log.info("[토스 페이먼츠 결제 승인 요청] : 주문번호(orderId) : {}", paymentDto.getOrderId());
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey +
                        ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedSecretKey);

        HttpEntity<TossPaymentRequestDto> httpEntity = new
                HttpEntity<>(paymentDto, headers);

        ResponseEntity<?> response =
                tossRestTemplate.exchange(

                        "https://api.tosspayments.com/v1/payments/confirm",
                        HttpMethod.POST,
                        httpEntity,
                        String.class
                );

        log.info("[토스 페이먼츠 결제 승인 완료] 응답 데이터 :{}", response.getBody());
        return null;

    }
}