package com.sh.aicommerce.enums.refund;

public enum RefundStatus {
//    READY,          // 환불 작업 준비
    PROCESSING,     // PG 취소 요청 처리 중
    COMPLETED,      // PG 취소 성공 확인 및 내부 반영 완료
    FAILED,         // 명확한 요청 실패
    FAILED_CHECK  // 타임아웃 등 실제 결과 확인 필요
}
