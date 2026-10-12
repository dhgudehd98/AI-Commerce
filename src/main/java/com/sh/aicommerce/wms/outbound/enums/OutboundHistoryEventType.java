package com.sh.aicommerce.wms.outbound.enums;

import lombok.Getter;

@Getter
public enum OutboundHistoryEventType {
    STATUS_CHANGED("출고 상태 변경"), // 출고 단계 변경 및 보류·해제
    CANCEL_REQUESTED("주문 취소 요청"), // 주문 취소 요청
    ITEMS_RETURNED("상품 복귀"), // 취소 상품의 창고 복귀 확인
    CANCEL_COMPLETED("출고 취소 완료"), // 출고 취소 완료 (환불 완료와 별개)
    PROCESSING_FAILED("출고 처리 실패");// 출고 처리 실패 ;

    private String type;

    OutboundHistoryEventType(String type) {
        this.type = type;
    }
}
