package com.sh.aicommerce.entity;


import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.enums.product.ProductCategory;
import com.sh.aicommerce.enums.product.ProductStatus;
import com.sh.aicommerce.product.dto.ProductCreateRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory productCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus productStatus;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private String productName;

    @Column(length = 2000)
    private String productDescription;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> productImages = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<ProductOption> productOptions = new ArrayList<>();

    public static Product create(ProductCreateRequestDto dto, Brand brand) {
        Product product = new Product();
        product.brand = brand;
        product.productName = dto.getProductName();
        product.productStatus = ProductStatus.PREPARING;
        product.productDescription = dto.getProductDescription();
        product.price = dto.getProductPrice();
        product.productCategory = dto.getProductCategory();

        return product;
    }

    //
    public void onSale() {
        this.productStatus = ProductStatus.ON_SALE;
    }
}
