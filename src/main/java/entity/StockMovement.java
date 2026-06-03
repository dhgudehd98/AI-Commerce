package entity;

import enums.wms.StockMovementStatus;
import enums.wms.StockMovementReferenceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_inventory_id", nullable = false)
    private ProductInventory productInventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementStatus stockMovementStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementReferenceType referenceType;

    private Long referenceId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer beforeQuantity;

    @Column(nullable = false)
    private Integer afterQuantity;

    private String reason;

    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

}
