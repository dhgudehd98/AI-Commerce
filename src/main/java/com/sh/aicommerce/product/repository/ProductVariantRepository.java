package com.sh.aicommerce.product.repository;

import com.sh.aicommerce.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByIdAndProductId(Long productVariantId, Long id);

    @Query(
    """
    select pv 
    from ProductVariant pv
    where pv.id=:variantId
    """
    )
    Optional<ProductVariant> findWithProductById(@Param("variantId")Long variantId);
}
