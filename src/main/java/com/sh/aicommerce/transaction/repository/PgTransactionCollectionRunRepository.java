package com.sh.aicommerce.transaction.repository;

import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface PgTransactionCollectionRunRepository extends JpaRepository<PgTransactionCollectionRun, Long> {
    Optional<PgTransactionCollectionRun> findByJobInstanceId(Long jobInstanceId);


    Optional<PgTransactionCollectionRun> findFirstByTargetDateOrderByJobExecutionIdDesc(LocalDate targetDate);

}
