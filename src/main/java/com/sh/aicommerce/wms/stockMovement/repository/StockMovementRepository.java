package com.sh.aicommerce.wms.stockMovement.repository;

import com.sh.aicommerce.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    java.util.List<StockMovement> findByReferenceTypeAndReferenceId(
            com.sh.aicommerce.enums.wms.StockMovementReferenceType referenceType, Long referenceId);
}
