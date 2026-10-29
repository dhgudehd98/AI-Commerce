package com.sh.aicommerce.paymentCancellation.repository;

import com.sh.aicommerce.entity.PaymentCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {

    @Query("""
    select pc
    from PaymentCancellation pc
    where pc.payment.id = :paymentId
    """)
    Optional<PaymentCancellation> findByPaymentId(@Param("paymentId") Long paymentId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select pc
    from PaymentCancellation pc
    where pc.orderCancellation.id = :orderCancellationId
    """)
    Optional<PaymentCancellation> findPaymentCancellationByOrderCancellationIdForUpdate(@Param("orderCancellationId")Long orderCancellationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select pc
    from PaymentCancellation pc
    where pc.id = :paymentCancellationId
    """)
    Optional<PaymentCancellation> findByIdForUpdate(@Param("paymentCancellationId")Long paymentCancellationId);
}
