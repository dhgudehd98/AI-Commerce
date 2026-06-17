package com.sh.aicommerce.wms.inBound.dto;

public record ProductInboundResponseRecord(
        Long productId,
        Long warehouseId,
        String result,
        String message
) {
}