package com.sh.aicommerce.search.dto;


import com.sh.aicommerce.brand.es.BrandDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BrandAutoCompletionDto {

    private Long brandId;
    private String brandName;
    private String imageUrl;

    public BrandAutoCompletionDto(BrandDocument document) {
        this.brandId = document.getBrandId();
        this.brandName = document.getBrandName();
        this.imageUrl = document.getBrandImageUrl();
    }
}