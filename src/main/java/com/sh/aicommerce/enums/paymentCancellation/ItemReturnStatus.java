package com.sh.aicommerce.enums.paymentCancellation;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ItemReturnStatus {

    NOT_REQUIRED("상품 복귀 불필요"),
    WAITING("상품 복귀 확인 대기"),
    RETURNED("상품 복귀 확인 완료"),
    MANUAL_REVIEW("관리자 확인 필요");

    private final String description;
}
