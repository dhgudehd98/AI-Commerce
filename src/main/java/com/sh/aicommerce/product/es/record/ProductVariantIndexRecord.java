package com.sh.aicommerce.product.es.record;

import com.sh.aicommerce.entity.ProductImage;
import com.sh.aicommerce.entity.ProductVariant;
import com.sh.aicommerce.enums.product.ProductImageType;

import java.util.Comparator;
import java.util.List;

public record ProductVariantIndexRecord(
        Long productVariantId,
        String variantName,
        String color,
        String modelNumber,
        Integer price,
        String productVariantStatus,
        String thumbnailUrl,
        List<String> imageUrls,
        List<ProductOptionIndexRecord> options
) {

    public static ProductVariantIndexRecord from(ProductVariant productVariant) {
        String thumbnailUrl = productVariant.getImages().stream()
                .filter(productImage -> productImage.getImageType() == ProductImageType.THUMBNAIL)
                .map(productImage -> productImage.getImageUrl())
                .findFirst()
                .orElse(null);

        List<String> imageUrls = productVariant.getImages().stream()
                .filter(productImages -> productImages.getImageType() != ProductImageType.THUMBNAIL)
                .sorted(Comparator.comparing(productImage-> productImage.getDisplayOrder()))
                .map(ProductImage::getImageUrl)
                .toList();

        return new ProductVariantIndexRecord(
                productVariant.getId(),
                productVariant.getVariantName(),
                productVariant.getColor(),
                productVariant.getModelNumber(),
                productVariant.getPrice(),
                String.valueOf(productVariant.getProductVariantStatus()),
                thumbnailUrl,
                imageUrls,
                productVariant.getOptions().stream()
                        .map(productOption -> ProductOptionIndexRecord.from(productOption))
                        .toList()
        );
    }
}