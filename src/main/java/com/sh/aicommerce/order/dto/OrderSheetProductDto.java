package com.sh.aicommerce.order.dto;

import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OrderSheetProductDto {
    Long productId;
    Long variantId;
    Long optionId;
    String variantName;
    String size; // 상품 사이즈
    Integer productPrice; // 상품 가격

    public OrderSheetProductDto(ProductVariant variant, ProductOption option) {
        this.productId = variant.getProduct().getId();
        this.variantId = variant.getId();
        this.optionId = option.getId();
        this.variantName = variant.getVariantName();
        this.size = option.getSize();
        this.productPrice = variant.getPrice() + option.getAdditionalPrice();
    }
}