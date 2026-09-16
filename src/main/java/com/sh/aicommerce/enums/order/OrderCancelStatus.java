package com.sh.aicommerce.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderCancelStatus {
    CANCEL_REQUEST("취소 요청"),
    PAYMENT_CANCEL("결제취소 요청"),
    ITEM_RETURNING("상품 복귀중"),
    FAILED("취소 실패"),
    COMPLETE("취소 처리 완료");

    private String status;
}