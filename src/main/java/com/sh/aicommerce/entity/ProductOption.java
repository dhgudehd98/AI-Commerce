package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.product.ProductOptionStatus;
import com.sh.aicommerce.product.dto.request.ProductOptionCreateRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String size;

    @Column(nullable = false)
    private Integer additionalPrice = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductOptionStatus status;

    @OneToMany(mappedBy = "productOption")
    private List<ProductInventory> inventories = new ArrayList<>();

    public static ProductOption createOption(
            ProductVariant productVariant,
            ProductOptionCreateRequestDto dto
    ) {
        ProductOption option = new ProductOption();
        option.productVariant = productVariant;
        option.sku = dto.getSku();
        option.size = dto.getSize();
        option.additionalPrice = dto.getAdditionalPrice();
        option.status = ProductOptionStatus.PREPARING;

        return option;
    }

    public void onSale() {
        this.status = ProductOptionStatus.AVAILABLE;
    }

    public Product getProduct() {
        return productVariant.getProduct();
    }
}
