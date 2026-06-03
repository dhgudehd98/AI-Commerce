package entity;

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
    private String optionNameSnapshot;

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

}
