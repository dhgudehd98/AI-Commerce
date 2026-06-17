package com.sh.aicommerce.wms.outbound.enums;

public enum OutboundStatus {
    PENDING, // 출고 요청 -> 실재 재고 / 차감 / 출고 전
    COMPLETED, // 출고 처리 완료(재고 차감 및 StockMovement 저장까지 완료된 상태)
    FAILED, // 출고 처리 실패
    CANCELED // 출고 요청 취소

}
