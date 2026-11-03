package com.sh.aicommerce.paymentCompensation.repository;

import com.sh.aicommerce.entity.PaymentCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentCompensationRepository extends JpaRepository<PaymentCompensation, Long> {
    Optional<PaymentCompensation> findByPaymentAttemptId(Long attemptId);
}
