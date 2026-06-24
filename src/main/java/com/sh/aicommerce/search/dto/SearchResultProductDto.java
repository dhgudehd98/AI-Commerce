package com.sh.aicommerce.search.dto;

import com.sh.aicommerce.product.es.document.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class SearchResultProductDto {

    private Long productVariantId;
    private String productVariantName;
    private String brandName;
    private String productDescription;
    private Integer price;
    private String thumbnailUrl;

    private float score;

    public SearchResultProductDto(ProductDocument document) {
        this.productVariantId = document.getProductVariantId();
        this.productVariantName = document.getVariantName();
        this.brandName = document.getBrandName();
        this.productDescription = document.getProductDescription();
        this.price = document.getPrice();
        this.thumbnailUrl = document.getThumbnailUrl();

    }
}