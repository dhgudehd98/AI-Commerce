package com.sh.aicommerce.toss.dto.response;


import lombok.Getter;

@Getter
public class TossPaymentResponseDto {
    // 공통 필드들
    private String paymentKey;
    private String orderId;
    private String method;
    private Long totalAmount;

    // 결제수단에 따라 둘 중 하나만 값이 들어옴
    private CardResponse card;
    private EasyPayResponse easyPay;
}