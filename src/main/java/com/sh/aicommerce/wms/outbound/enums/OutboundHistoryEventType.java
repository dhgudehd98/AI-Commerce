package com.sh.aicommerce.wms.outbound.enums;

public enum OutboundHistoryEventType {
    STATUS_CHANGED, // 출고 단계 변경 및 보류·해제
    CANCEL_REQUESTED, // 주문 취소 요청
    ITEMS_RETURNED, // 취소 상품의 창고 복귀 확인
    CANCEL_COMPLETED, // 출고 취소 완료 (환불 완료와 별개)
    PROCESSING_FAILED // 출고 처리 실패
}
