package com.sh.aicommerce.product.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class ProductOptionCreateRequestDto {

    @NotBlank
    private String sku;

    @NotBlank
    private String color;

    @NotBlank
    private String size;

    @NotNull
    @PositiveOrZero
    private Integer additionalPrice; // 색상 / 사이즈별 추가 금액
}