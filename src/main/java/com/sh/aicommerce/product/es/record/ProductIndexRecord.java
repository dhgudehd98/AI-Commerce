package com.sh.aicommerce.product.es.record;

import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.enums.product.ProductCategory;

import java.util.ArrayList;
import java.util.List;

public record ProductIndexRecord(
        Long productId,
        Long brandId,
        String brandName,
        String productCategory,
        String baseProductName,
        String productDescription,
        List<String> tags,
        List<ProductVariantIndexRecord> variantRecords
) {
    public static ProductIndexRecord from(Product product) {
        return new ProductIndexRecord(
            product.getId(),
            product.getBrand().getId(),
            product.getBrand().getBrandName(),
            String.valueOf(product.getProductCategory()),
            product.getBaseProductName(),
            product.getProductDescription(),
            new ArrayList<>(product.getTags()),
            product.getVariants().stream()
                    .map(ProductVariantIndexRecord::from)
                    .toList()
        );
    }
}