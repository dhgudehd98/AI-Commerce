package com.sh.aicommerce.entity;


import com.sh.aicommerce.product.dto.request.ProductVariantRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "product_variant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_variant_model_number",
                        columnNames = "model_number"
                ),
                @UniqueConstraint(
                        name = "uk_product_variant_product_color",
                        columnNames = {"product_id" , "color"}
                )
        }
)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_variant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ProductOption> options = new ArrayList<>();

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(nullable = false)
    private String color;

    @Column(name = "model_number", nullable = false)
    private String modelNumber;

    @Column(nullable = false)
    private Integer price;

    public static ProductVariant create(
            Product product,
            ProductVariantRequestDto dto
    ) {
        ProductVariant variant = new ProductVariant();
        variant.product = product;
        variant.variantName = dto.getVariantName();
        variant.color = dto.getColor();
        variant.modelNumber = dto.getModelNumber();
        variant.price = dto.getPrice();
        return variant;
    }
}
