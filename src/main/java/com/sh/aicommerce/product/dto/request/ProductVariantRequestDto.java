package com.sh.aicommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantRequestDto {

    @NotBlank
    private String variantName;

    @NotBlank
    private String color;

    @NotBlank
    private String modelNumber;

    @NotNull
    @Positive
    private Integer price;

    @NotEmpty
    private List<@Valid ProductImageCreateRequestDto> images;

    @NotEmpty
    private List<@Valid ProductOptionCreateRequestDto> productOptions;
}
