package com.sh.aicommerce.enums.wms;

public enum StockMovementReferenceType {
    INITIAL_INBOUND, // 초기 입고
    ADDITIONAL_INBOUND, // 재고 추가 입고
    ORDER, // 주문
    ORDER_CANCELLATION, // 주문 취소 ID
    REFUND, // 환불
    MANUAL_ADJUSTMENT // 관리자가 수정하는거
}
