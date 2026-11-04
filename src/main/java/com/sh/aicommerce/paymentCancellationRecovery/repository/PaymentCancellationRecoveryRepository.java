package com.sh.aicommerce.paymentCancellationRecovery.repository;

import com.sh.aicommerce.entity.PaymentCancellationRecovery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCancellationRecoveryRepository extends JpaRepository<PaymentCancellationRecovery, Long> {

    @Query(
    """
    select pcr
    from PaymentCancellationRecovery pcr
    where pcr.paymentCancellation.id = :paymentCancellationId
    """)
    Optional<PaymentCancellationRecovery> findRecoveryByPaymentCancellationId(@Param("paymentCancellationId")Long paymentCancellationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select pcr
            from PaymentCancellationRecovery pcr
            where pcr.id = :recoveryId
            """)
    Optional<PaymentCancellationRecovery> findRecoveryByPaymentCancellationIdForUpdate(@Param("recoveryId")Long paymentCancellationId);
}
