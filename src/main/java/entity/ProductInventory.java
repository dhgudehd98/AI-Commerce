package entity;

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
public class ProductInventory { // 옵션별 창고별 수량


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
    private Integer quantity;

    // 최소 안전 재고
    @Column(nullable = false)
    private Integer safetyQuantity;

    @OneToMany(mappedBy = "productInventory")
    List<StockMovement> stockMovements = new ArrayList<>();

}