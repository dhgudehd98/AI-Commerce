package com.sh.aicommerce.enums.paymentCompensation;

public enum PaymentCompensationStatus {
    PENDING,
    PROCESSING,
    CANCELLATION_PENDING, // PG 취소 작업에 인계됨. 재고 등 후속 작업 완료 전
    FOLLOW_UP_PENDING, // 후속 처리 대기 상태
    COMPLETED,
    MANUAL_REVIEW_REQUIRED
}
