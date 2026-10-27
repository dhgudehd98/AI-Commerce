package com.sh.aicommerce.enums.paymentCancellation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentCancellationStatus {

    READY("결제 취소 요청 대기"),
    PROCESSING("결제 취소 요청 진행 중"),
    SUCCESS("결제 취소 완료"),
    REJECTED("결제 취소 요청 거절"),
    FAILED("결제 취소 요청 실패"),
    UNKNOWN("결제 취소 결과 확인 필요"),
    MANUAL_REVIEW_REQUIRED("관리자 확인 필요");

    private final String description;
}
