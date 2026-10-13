package com.sh.aicommerce.enums.payment;

public enum PaymentAttemptStatus {
    READY,       // 승인 시도 생성 완료: 아직 PG 승인 요청을 시작하지 않음

    PROCESSING,  // PG 승인 요청 진행 중: 응답 대기 또는 처리 중

    SUCCESS,     // 승인 성공: PG에서 결제 성공을 확인함

    FAILED,      // 승인 실패 확정: PG에서 해당 시도의 실패가 확인됨

    UNKNOWN      // 승인 결과 불명확: 타임아웃, 응답 유실 등으로 PG 결과 확인 필요
}