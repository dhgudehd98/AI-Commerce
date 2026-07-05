package com.sh.aicommerce.entity;


import com.sh.aicommerce.enums.product.ProductCategory;
import com.sh.aicommerce.enums.product.ProductStatus;
import com.sh.aicommerce.product.dto.request.ProductCreateRequestDto;
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

    @Column(name = "base_product_name", nullable = false)
    private String baseProductName;

    @Column(length = 2000)
    private String productDescription;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "product_tags",
            joinColumns = @JoinColumn(name = "product_id")
    )
    private List<String> tags = new ArrayList<>();

    public static Product create(ProductCreateRequestDto dto, Brand brand) {
        Product product = new Product();
        product.brand = brand;
        product.baseProductName = dto.getBaseProductName();
        product.productDescription = dto.getProductDescription();
        product.productCategory = dto.getProductCategory();
        product.tags = new ArrayList<>(dto.getTags());

        return product;
    }
}
