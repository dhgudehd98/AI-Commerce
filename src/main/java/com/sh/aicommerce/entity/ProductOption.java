package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.product.ProductOptionStatus;
import com.sh.aicommerce.product.dto.ProductOptionCreateRequestDto;
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

    // 조회에 대한 성능을 향상 시키기 위해서 Product에 대한 값 저장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private String size;

    @Column(nullable = false)
    private Integer additionalPrice = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductOptionStatus status;

    @OneToMany(mappedBy = "productOption")
    private List<ProductInventory> inventories = new ArrayList<>();

    public static ProductOption createOption(ProductOptionCreateRequestDto dto, Product product) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.sku = dto.getSku();
        option.color = dto.getColor();
        option.size = dto.getSize();
        option.additionalPrice = dto.getAdditionalPrice();
        option.status = ProductOptionStatus.PREPARING;

        return option;
    }

    public void onSale() {
        this.status = ProductOptionStatus.AVAILABLE;
    }
}
