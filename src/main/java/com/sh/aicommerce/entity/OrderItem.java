package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @Column(nullable = false)
    private String productNameSnapshot;

    @Column(nullable = false)
    private String brandNameSnapshot;

    @Column(nullable = false)
    private String skuSnapshot;

    @Column(nullable = false)
    private Integer basePriceSnapshot;

    @Column(nullable = false)
    private Integer additionalPriceSnapshot;

    @Column(nullable = false)
    private Integer unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer lineTotalPrice;

    public static OrderItem createOrderItem(ProductOption option) {
        OrderItem item = new OrderItem();
        item.productOption = option;
        item.productNameSnapshot = option.getProductVariant().getVariantName();
        item.brandNameSnapshot = option.getProduct().getBrand().getBrandName();
        item.skuSnapshot = option.getSku();
        item.basePriceSnapshot = option.getProductVariant().getPrice();
        item.additionalPriceSnapshot = option.getAdditionalPrice();
        item.unitPrice = item.basePriceSnapshot + item.additionalPriceSnapshot;
        item.quantity = 1;
        item.lineTotalPrice = item.unitPrice * item.quantity;

        return item;
    }

    public void setOrder(Orders order) {
        this.order = order;
    }
}
