package com.sh.aicommerce.wms.inventory.repository;

import com.sh.aicommerce.entity.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select pi 
        from ProductInventory pi
        where pi.productOption.id = :productOptionId
        and   pi.warehouse.id = :warehouseId
    """)
    Optional<ProductInventory> findByProductOptionIdAndWarehouseIdForUpdate(@Param("productOptionId") Long productOptionId,@Param("warehouseId") Long warehouseId);

    boolean existsByProductOptionIdAndWarehouseId(Long optionId, Long warehouseId);
}
