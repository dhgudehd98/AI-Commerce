package com.sh.aicommerce.paymentCancellationRecovery.repository;

import com.sh.aicommerce.entity.PaymentCancellationRecoveryHistory;
import com.sh.aicommerce.entity.PaymentRecoveryHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCancellationRecoveryHistoryRepository extends JpaRepository<PaymentCancellationRecoveryHistory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select h
    from PaymentCancellationRecoveryHistory h
    where h.id = :cancellationRecoveryHistoryId
    """)
    Optional<PaymentCancellationRecoveryHistory> findByIdForUpdate(@Param("cancellationRecoveryHistoryId") Long historyId);
}