package com.sh.aicommerce.enums.paymentRecovery;

public enum PaymentRecoveryResult {
    CREATE,        // 실행 이력 생성 -> 결과 미확정
    SUCCESS      , // 작업 성공
    FAILED       , // 작업 실행 실패
    INCONCLUSIVE  // 결제 결과 확정 불가
}
