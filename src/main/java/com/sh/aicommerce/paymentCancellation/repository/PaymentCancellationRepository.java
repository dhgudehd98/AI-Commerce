package com.sh.aicommerce.paymentCancellation.repository;

import com.sh.aicommerce.entity.PaymentCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
