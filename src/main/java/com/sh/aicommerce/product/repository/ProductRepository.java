package com.sh.aicommerce.product.repository;

import com.sh.aicommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p join fetch p.brand where p.id = :productId")
    Optional<Product> findWithBrandByproductId(@Param("productId") Long productId);

    @Query("select p from Product p where p.id = :productId and p.findWithBrandAndVariantsByProductId <> 'STOPPED'")
    Optional<Product> findNoStoppedProduct(@Param("productId") Long productId);

    @Query("""
            select p 
            from Product p
            join fetch p.brand
            left join fetch p.variants v
            where p.id = :productId
            """)
    Optional<Product> findWithBrandAndVariantsByProductId(@Param("productId") Long productId);

}