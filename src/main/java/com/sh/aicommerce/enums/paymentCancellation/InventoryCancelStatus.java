package com.sh.aicommerce.enums.paymentCancellation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InventoryCancelStatus {
    PENDING("재고 처리 대기"),
    PROCESSING("재고 처리 중"),
    COMPLETED("재고 처리 완료"),
    FAILED("재고 처리 실패"),
    NOT_REQUIRED("재고 처리 불필요");

    private final String description;
}