package com.sh.aicommerce.product.redis;

public record ProductIndexEventRecord(
        Long productId,
        String action
) {

}
