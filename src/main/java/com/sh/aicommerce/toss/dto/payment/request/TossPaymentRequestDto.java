package com.sh.aicommerce.toss.dto.payment.request;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentRequestDto {
    private String paymentKey;
    private Integer amount;
    private String orderId;
}