package com.sh.aicommerce.enums.payment;

import jakarta.persistence.LockModeType;
import lombok.Getter;
import org.springframework.data.jpa.repository.Lock;

@Getter
public enum PaymentStatus {
    READY("결제 준비"),
    // 결제 승인 요청 확인 Status
    CONFIRMING("결제 확인"),
    PAID("결제 완료"),
    FAILED("결제 실패"),
    CANCELED("결제 취소"),
    CANCEL_FAILED("결제 취소 실패"),
    REFUNDED("환불");

    private String status;

    PaymentStatus(String status) {
        this.status = status;
    }
}
