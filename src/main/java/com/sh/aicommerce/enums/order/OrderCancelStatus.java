package com.sh.aicommerce.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderCancelStatus {

    REQUESTED("주문 취소 요청"),
    PROCESSING("주문 취소 처리 중"),
    COMPLETED("주문 취소 완료"),
    FAILED("주문 취소 처리 실패"),
    MANUAL_REVIEW_REQUIRED("관리자 확인 필요"),
    REJECTED("주문 취소 거절"),
    UNKNOWN("주문 취소 결과 확인 필요");


    private String status;
}