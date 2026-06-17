package com.sh.aicommerce.entity;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.StockMovement;
import com.sh.aicommerce.entity.Warehouse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Table(
        name = "product_inventory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_inventory_option_warehouse",
                        columnNames = {"product_option_id", "warehouse_id"}
                )
        }
)
public class ProductInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_inventory_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private Integer onHandQuantity;

    @Column(nullable = false)
    private Integer reservedQuantity;

    @Column(nullable = false)
    private Integer safetyQuantity; // 최소 유지 수량

    @OneToMany(mappedBy = "productInventory")
    private List<StockMovement> stockMovements = new ArrayList<>();

    public int getAvailableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public ProductInventory(ProductOption productOption, Warehouse warehouse, Integer onHandQuantity, Integer safetyQuantity) {
        this.productOption = productOption;
        this.warehouse = warehouse;
        this.onHandQuantity = onHandQuantity;
        this.safetyQuantity = safetyQuantity;
    }

    public static ProductInventory create(
            ProductOption productOption,
            Warehouse warehouse,
            Integer quantity,
            Integer safetyQuantity
    ) {
        if(safetyQuantity <= 0 || quantity <= 0) throw new InventoryException("안전 재고나 입고수량에 대한 값은 음수가 될 수 없습니다.");

        ProductInventory inventory = new ProductInventory();
        inventory.productOption = productOption;
        inventory.warehouse = warehouse;
        inventory.onHandQuantity = quantity;
        inventory.reservedQuantity = 0; // 초기 입고 했을 때는 reservedQuantity에 대한 값은 0으로 설정하고 사용자가 주문을 했을 때 추가하는 형식으로 설정
        inventory.safetyQuantity = safetyQuantity;

        return inventory;
    }

    // 입고되어 있는 상품 물량 증가
    public void increaseOnHandQuantity(Integer quantity) {
        if(quantity <= 0) throw new InventoryException("입고 수량은 1개 이상이여야 합니다.");
        this.onHandQuantity += quantity;
    }

    // 입고되어 있는 상품 물량 감소
    public void decreaseOnHandQuantity(Integer quantity) {
        if(quantity <= 0) throw new InventoryException("출고 수량은 1개 이상이여야합니다.");
    }

}