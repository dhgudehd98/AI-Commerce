package com.sh.aicommerce.product.repository;

import com.sh.aicommerce.entity.ProductIndexFailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductIndexFailLogRepository extends JpaRepository<ProductIndexFailLog, Long> {
}
