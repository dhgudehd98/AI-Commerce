package com.sh.aicommerce.product.es.record;

import com.sh.aicommerce.entity.ProductInventory;
import com.sh.aicommerce.entity.ProductOption;

public record ProductOptionIndexRecord(
        Long optionId,
        String sku,
        String size,
        Integer additionalPrice,
        String optionStatus,
        Integer availableStock
) {
    public Integer totalPrice(Integer variantPrice) {
        return variantPrice + additionalPrice;
    }

    public Boolean inStock() {
        return availableStock != null && availableStock > 0;
    }

    public static ProductOptionIndexRecord from(ProductOption option) {
        int availableStock = option.getInventories().stream()
                .mapToInt(ProductInventory::getAvailableQuantity)
                .sum();

        return new ProductOptionIndexRecord(
                option.getId(),
                option.getSku(),
                option.getSize(),
                option.getAdditionalPrice(),
                java.lang.String.valueOf(option.getStatus()),
                availableStock
        );
    }
}