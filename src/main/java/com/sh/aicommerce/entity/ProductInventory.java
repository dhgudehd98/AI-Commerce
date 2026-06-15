package com.sh.aicommerce.entity;
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
    private Integer safetyQuantity;

    @Version
    private Long version;

    @OneToMany(mappedBy = "productInventory")
    private List<StockMovement> stockMovements = new ArrayList<>();

    public int getAvailableQuantity() {
        return onHandQuantity - reservedQuantity;
    }
}