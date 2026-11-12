package com.sh.aicommerce.transactionReconciliation.repository;

import com.sh.aicommerce.entity.PgTransactionReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PgTransactionReconciliationResultRepository extends JpaRepository<PgTransactionReconciliationResult, Long> {
}
