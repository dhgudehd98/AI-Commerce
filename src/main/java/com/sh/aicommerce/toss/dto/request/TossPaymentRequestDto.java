package com.sh.aicommerce.toss.dto.request;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentRequestDto {
    private String paymentKey;
    private Integer amount;
    private String orderId;
}