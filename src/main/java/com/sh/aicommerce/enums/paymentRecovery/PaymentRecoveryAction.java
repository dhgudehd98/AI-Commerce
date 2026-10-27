package com.sh.aicommerce.enums.paymentRecovery;

public enum PaymentRecoveryAction {
    PG_STATUS_CHECK ,// PG 결제 결과 조회
    DB_APPLY        ,// 내부 DB 상태 반영
    MANUAL_RECONCILIATION ; // 운영자 수동 복구
}
