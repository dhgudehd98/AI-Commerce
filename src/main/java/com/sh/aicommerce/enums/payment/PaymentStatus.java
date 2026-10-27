package com.sh.aicommerce.enums.payment;

import jakarta.persistence.LockModeType;
import lombok.Getter;
import org.springframework.data.jpa.repository.Lock;

@Getter
public enum PaymentStatus {
    READY("결제 준비"), // 결제 준비 상태 : PG 승인 요청 전
    PROCESSING("결제 요청 진행중"), // 결제 승인 진행 중 : PG 요청 처리 중이거나 결과 확인이 필요함.
    PAID("결제 완료"), // 결제 완료 : PG에서 승인 성공이 확인도니 상태
    PAYMENT_REJECTED("결제 승인 거절"), // PG가 명확한 승인 거절을 응답
    PAYMENT_FAILED("결제 복구 최종 실패"), // 거래 미조회 복구 횟수 소진으로 종료
    CANCELED("결제 취소 완료"),
    CANCEL_FAILED("결제 취소 실패"),
    UNKNOWN("결과 확인 필요");

    private String status;

    PaymentStatus(String status) {
        this.status = status;
    }
}
