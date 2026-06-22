package com.sh.aicommerce.product.es.document;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;




@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProductOptionDocument {

    private Long optionId;
    private String sku;
    private String size;
    private Integer additionalPrice;
    private Integer totalPrice;
    private String optionStatus;
    private Integer availableStock;
    private Boolean inStock;
}