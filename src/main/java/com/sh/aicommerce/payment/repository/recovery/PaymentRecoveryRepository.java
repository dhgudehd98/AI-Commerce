package com.sh.aicommerce.payment.repository.recovery;

import com.sh.aicommerce.entity.PaymentRecovery;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRecoveryRepository extends JpaRepository<PaymentRecovery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select pr
    from PaymentRecovery pr 
    where pr.paymentAttempt.id = :attemptId
    """)
    Optional<PaymentRecovery> findPaymentRecoveryByAttemptIdForUpdate(@Param("attemptId") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select pr
    from PaymentRecovery  pr
    where pr.id = :recoveryId
    """)
    Optional<PaymentRecovery> findRecoveryByIdForUpdate(@Param("recoveryId")Long recoveryId);
}
