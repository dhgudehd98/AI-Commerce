package com.sh.aicommerce.enums.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public enum PaymentStatus {
    READY,
    // 결제 승인 요청 확인 Status
    CONFIRMING,
    PAID,
    FAILED,
    CANCELED,
    REFUNDED
}
