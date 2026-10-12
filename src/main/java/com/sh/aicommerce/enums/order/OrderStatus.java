package com.sh.aicommerce.enums.order;

import lombok.Getter;

@Getter
public enum OrderStatus {
    CREATED("주문 생성"),
    FAILED("주문 실패"),
    PAID("주문 결제 완료"),
    PREPARING("주문 준비"),
    SHIPPED("주문 배송"),
    DELIVERED("주문 배송 완료"),
    CANCEL_REQUEST("주문 취소 요청"),
    CANCELED("주문 취소"),
    REFUNDED("주문 환불");

    private String status;

    OrderStatus(String status) {
        this.status = status;
    }
}
