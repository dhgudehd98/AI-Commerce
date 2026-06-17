package com.sh.aicommerce.wms.inventory.repository;

import com.sh.aicommerce.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {

    Optional<ProductInventory> findByProductOptionIdAndWarehouseId(Long productOptionId, Long warehouseId);
}
