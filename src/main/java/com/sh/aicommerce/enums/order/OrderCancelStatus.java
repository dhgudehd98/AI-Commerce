package com.sh.aicommerce.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderCancelStatus {
    CANCEL_REQUEST("취소요청"),
    PROCESSING("진행중"),
    FAILED("실패"),
    COMPLETE("취소완료");

    private String status;
}