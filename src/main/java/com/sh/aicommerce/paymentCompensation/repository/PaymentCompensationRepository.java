package com.sh.aicommerce.paymentCompensation.repository;

import com.sh.aicommerce.entity.PaymentCompensation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCompensationRepository extends JpaRepository<PaymentCompensation, Long> {
    Optional<PaymentCompensation> findByPaymentAttemptId(Long attemptId);


    @Query("""
    select pc
    from PaymentCompensation pc
    join fetch pc.paymentAttempt pa
    where pc.id = :compensationId
    """)
    Optional<PaymentCompensation> findPaymentAndAttemptById(@Param("compensationId")Long compensationId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCompensation c where c.id = :id")
    Optional<PaymentCompensation> findByIdForUpdate(@Param("id") Long id);

    java.util.List<PaymentCompensation> findAllByPaymentCancellationId(Long cancellationId);
}
