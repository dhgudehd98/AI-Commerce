package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.wms.StockMovementStatus;
import com.sh.aicommerce.enums.wms.StockMovementReferenceType;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementStatus stockMovementStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementReferenceType referenceType;

    private Long referenceId; // 주문이면 주문 ID, 입고면 입고 ID

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

    public StockMovement(ProductInventory productInventory, StockMovementStatus stockMovementStatus, StockMovementReferenceType referenceType, Long referenceId, Integer quantity, Integer beforeQuantity, Integer afterQuantity, LocalDateTime createdAt) {
        this.productInventory = productInventory;
        this.stockMovementStatus = stockMovementStatus;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.quantity = quantity;
        this.beforeQuantity = beforeQuantity;
        this.afterQuantity = afterQuantity;
        this.createdAt = createdAt;
    }
}
