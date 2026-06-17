package com.sh.aicommerce.productOption.repository;

import com.sh.aicommerce.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    boolean existsBySku(String sku);

    int countBySku(String duplicatedSku);

    Optional<ProductOption> findByProductId(Long id);

    void deleteByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("select o from ProductOption o where o.product.id = :productId and o.id = :optionId and o.status <> 'HIDDEN'")
    Optional<ProductOption> findByProductIdAndNoHiddenProductOption(@Param("productId") Long productId, @Param("optionId") Long productOptionId);
}
