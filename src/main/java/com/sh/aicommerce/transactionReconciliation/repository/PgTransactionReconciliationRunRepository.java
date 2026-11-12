package com.sh.aicommerce.transactionReconciliation.repository;

import com.sh.aicommerce.entity.PgTransactionReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgTransactionReconciliationRunRepository extends JpaRepository<PgTransactionReconciliationRun, Long> {
}