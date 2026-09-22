package com.sh.aicommerce.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderCancelStatus {
    CANCEL_REQUEST("취소 요청"),
    PROCESSING_START("취소 처리 시작"),
    PRODUCT_RESERVATION_RELEASED("선점 재고 예약 해제 완료"),
    PRODUCT_RESERVATION_RELEASE_FAILED("선점 재고 예약 해제 실패"),
    PAYMENT_CANCEL("결제취소 요청"),
    PAYMENT_CANCEL_FAILED("PG사 결제취소 요청 실패"),
    ITEM_RETURN_REQUEST("상품 복귀 요청"),
    ITEM_RETURNING("상품 복귀중"),
    ITEM_RETURNED("상품 복귀 완료"),
    ITEM_RETURN_FAILED("상품 복귀 실패"),
    CANCELED("취소 처리 완료");

    private String status;
}