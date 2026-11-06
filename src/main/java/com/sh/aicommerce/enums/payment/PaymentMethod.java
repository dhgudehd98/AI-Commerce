package com.sh.aicommerce.enums.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentMethod {

    SAVED_ACCOUNT("계좌 간편 결제"), // 계좌 간편 결제 -> 등록된 계좌 결제
    SAVED_CARD("카드 결제"), // 카드 결제 -> 등록된 카드 결제
    TOSS("일반 토스 결제 "); // 일반 결제

    private String method;

}