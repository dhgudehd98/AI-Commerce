package com.sh.aicommerce.paymentAttempt.repository;

import com.sh.aicommerce.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select pa
    from PaymentAttempt pa
    where pa.id = :paymentAttemptId
    """)
    Optional<PaymentAttempt> findPaymentAttemptByAttemptIdForUpdate(@Param("paymentAttemptId")Long paymentAttemptId);

    Optional<PaymentAttempt> findFirstByPaymentIdOrderByIdDesc(Long id);
}
