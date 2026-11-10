package com.sh.aicommerce.transaction.repository;

import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PgTransactionCollectionRunRepository extends JpaRepository<PgTransactionCollectionRun, Long> {
    Optional<PgTransactionCollectionRun> findByJobInstanceId(Long jobInstanceId);
}
