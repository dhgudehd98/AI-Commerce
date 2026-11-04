package com.sh.aicommerce.enums.paymentCompensation;

public enum PaymentCompensationResult {
    PAYMENT_NOT_FOUND, // PG 조회 내역 없음으로 내부 취소 종료
    APPROVAL_ABORTED,  // PG 승인 실패로 내부 취소 종료
    PAYMENT_EXPIRED,   // PG 거래 만료로 내부 취소 종료
    PG_CANCELED        // 실제 PG 승인 거래의 취소 완료
}
