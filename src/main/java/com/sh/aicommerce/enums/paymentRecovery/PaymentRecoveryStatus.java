package com.sh.aicommerce.enums.paymentRecovery;

public enum PaymentRecoveryStatus {
    WAITING ,     //: 최초 복구 대기
    PROCESSING    ,// 복구 진행 중
    RETRY_WAIT    ,// 다음 복구 시도 대기
    COMPLETED     ,// 복구 완료
    MANUAL_REVIEW ,// 운영자 확인 필요 ->
}
