package com.sh.aicommerce.transaction.repository;

import com.sh.aicommerce.entity.PgTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PgTransactionRepository extends JpaRepository<PgTransaction, Long> {

    @Query("""
        select t
        from PgTransaction t
        where t.mId = :mid
          and t.transactionKey = :transactionKey
        """)
    Optional<PgTransaction> findByMIdAndTransactionKey(
            @Param("mid") String mId,
            @Param("transactionKey") String transactionKey
    );

    long countByCollectionRunId(Long collectionRunId);

    List<PgTransaction> findByCollectionRunIdOrderByIdAsc(Long collectionRunId);
}