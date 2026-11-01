package com.sh.aicommerce.enums.wms;

public enum StockMovementStatus {
    RESERVATION_RELEASE, // 예약 해제 (before/after는 예약 수량)
    INBOUND, // 입고
    OUTBOUND, // 출고
    ADJUSTMENT // 관리자가 조정
}