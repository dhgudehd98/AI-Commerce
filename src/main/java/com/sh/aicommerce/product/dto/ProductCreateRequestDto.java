package com.sh.aicommerce.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductCreateRequestDto {
    @NotNull
    private Long brandId;

    @NotNull
    private String productName;

    @NotNull
    private String productCategory;

    @NotNull
    private Integer productPrice;

    @NotNull
    private String productDescription;

    private List<ProductOptionCreateRequestDto> productOptions;

    @Override
    public String toString() {
        return "ProductCreateRequestDto{" +
                "brandId=" + brandId +
                ", productName='" + productName + '\'' +
                ", productCategory='" + productCategory + '\'' +
                ", productPrice=" + productPrice +
                ", productDescription='" + productDescription + '\'' +
                ", productOptions=" + productOptions +
                '}';
    }
}