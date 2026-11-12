package com.sh.aicommerce.transactionReconciliation.repository;

import com.sh.aicommerce.entity.PgTransactionReconciliationIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PgTransactionReconciliationIssueRepository extends JpaRepository<PgTransactionReconciliationIssue, Long> {
}
