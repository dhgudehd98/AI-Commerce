package com.sh.aicommerce.paymentLedger.repository;

import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, Long> {
    boolean existsByPaymentIdAndType(Long paymentId, PaymentLedgerType type);

    Optional<PaymentLedger> findByPaymentIdAndType(Long paymentId, PaymentLedgerType type);

    Optional<PaymentLedger> findByPaymentIdAndPaymentCancellationIdAndType(Long paymentId, Long paymentCancellationId, PaymentLedgerType paymentLedgerType);


    Optional<PaymentLedger> findByTransactionKeyAndType(String transactionKey, PaymentLedgerType paymentLedgerType);

}
