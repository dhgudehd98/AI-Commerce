package com.sh.aicommerce.product.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductOptionCreateRequestDto {

    @NotBlank
    @NotNull
    private String sku;

    @NotBlank
    @NotNull
    private String color;

    @NotBlank
    @NotNull
    private String size;

    @NotNull
    @PositiveOrZero
    private Integer additionalPrice; // 색상 / 사이즈별 추가 금액
}