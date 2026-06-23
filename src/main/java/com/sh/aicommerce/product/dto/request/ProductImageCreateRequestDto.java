package com.sh.aicommerce.product.dto.request;

import com.sh.aicommerce.enums.product.ProductImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductImageCreateRequestDto {

    @NotBlank
    private String imageUrl;

    @NotBlank
    private String objectKey;

    @NotNull
    private ProductImageType imageType;

    @NotNull
    @Positive
    private Integer displayOrder; // 1 : 첫번쨰 이미지 , 2 : 두번째 이미지, 3 : 세번째 이미지
}
