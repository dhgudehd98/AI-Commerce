package com.sh.aicommerce.product.dto;

import com.sh.aicommerce.enums.product.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotBlank
    private String productName;

    @NotNull(message = "상품 카테고리는 필수입니다.")
    private ProductCategory productCategory;

    @NotNull
    @Positive
    private Integer productPrice;

    @NotBlank
    private String productDescription;

    @NotEmpty
    private List<@Valid ProductOptionCreateRequestDto> productOptions;
}