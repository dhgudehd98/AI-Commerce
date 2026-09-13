package com.sh.aicommerce.wms.outbound.enums;

import lombok.Getter;

@Getter
public enum OutboundStatus {


    PENDING("출고요청"), // 출고요청
    ON_HOLD("출고보류"), // 출고 보류
    PICKING("피킹시작"), // 피킹시작
    PICKED("피킹완료"),
    PACKING("포장시작"), // 포장
    PACKED("포장완료"),
    DELIVERY_REQUEST("인계요청"), // 인계요청
    COMPLETED("출고완료"), //완료
    CANCELED("출고취소"), // 주문취소
    FAILED("출고실패");

    private String status;

    OutboundStatus(String status) {
        this.status = status;
    }
}
