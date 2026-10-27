package com.sh.aicommerce.payment.repository.history;

import com.sh.aicommerce.entity.PaymentRecoveryHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRecoveryHistoryRepository extends JpaRepository<PaymentRecoveryHistory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select prh
    from PaymentRecoveryHistory prh
    where prh.id = :historyId
    """)
    Optional<PaymentRecoveryHistory> findHistoryByIdForUpdate(@Param("historyId")Long historyId);
}
