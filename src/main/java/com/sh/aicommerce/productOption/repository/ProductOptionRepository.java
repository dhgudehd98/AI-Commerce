package com.sh.aicommerce.productOption.repository;

import com.sh.aicommerce.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    boolean existsBySku(String sku);

    int countBySku(String duplicatedSku);
}
