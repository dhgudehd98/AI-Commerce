package com.sh.aicommerce.product.dto.request;

import com.sh.aicommerce.enums.product.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank
    private String baseProductName;

    @NotNull(message = "상품 카테고리는 필수입니다.")
    private ProductCategory productCategory;

    @NotBlank
    private String productDescription;

    @NotEmpty
    private List<@Valid ProductVariantRequestDto> variants;

    @NotEmpty
    private List<String> tags;
}
