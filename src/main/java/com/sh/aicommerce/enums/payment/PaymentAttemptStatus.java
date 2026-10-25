package com.sh.aicommerce.enums.payment;

public enum PaymentAttemptStatus {
    READY,       // 승인 시도 생성 완료: 아직 PG 승인 요청을 시작하지 않음

    PROCESSING,  // PG 승인 요청 진행 중: 응답 대기 또는 처리 중

    SUCCESS,     // 승인 성공: PG에서 결제 성공을 확인함

    PAYMENT_REJECTED, // PG가 명확한 승인 거절을 응답

    UNKNOWN,      // 승인 결과 불명확: 타임아웃, 응답 유실 등으로 PG 결과 확인 필요
    PAYMENT_FAILED // 최종적으로 결제 승인이 실패한 상태(상태 복구 과정도 실패 )
}