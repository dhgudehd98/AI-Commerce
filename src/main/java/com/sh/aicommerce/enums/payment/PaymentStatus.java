package com.sh.aicommerce.enums.payment;

import jakarta.persistence.LockModeType;
import lombok.Getter;
import org.springframework.data.jpa.repository.Lock;

@Getter
public enum PaymentStatus {
    READY("결제 준비"), // 결제 준비 상태 : PG 승인 요청 전
    CONFIRMING("결제 확인"),
    PROCESSING("결제 요청 진행중"), // 결제 승인 진행 중 : PG 요청 처리 중이거나 결과 확인이 필요함.
    PAID("결제 완료"), // 결제 완료 : PG에서 승인 성공이 확인도니 상태
    FAILED("결제 실패"), // 결제 실패 : 결제 프로세스가 최종적으로 실패한 상태
    CANCELED("결제 취소 완료");
//    CANCEL_FAILED("결제 취소 실패");

    private String status;

    PaymentStatus(String status) {
        this.status = status;
    }
}
