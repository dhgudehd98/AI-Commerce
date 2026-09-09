package com.sh.aicommerce.wms.outbound.enums;

public enum OutboundStatus {
    PENDING, // 출고대기
    ON_HOLD, // 출고 보류
    PICKING, // 피킹
    PACKING, // 포장
    DELIVERY_REQUEST, // 인계요청
    COMPLETED, //완료
    CANCELED, // 주문취소
    FAILED

}
