package com.sh.aicommerce.paymentLedger.repository;

import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, Long> {
    boolean existsByPaymentIdAndType(Long paymentId, PaymentLedgerType type);

    Optional<PaymentLedger> findByPaymentIdAndType(Long paymentId, PaymentLedgerType type);

    Optional<PaymentLedger> findByPaymentIdAndPaymentCancellationIdAndType(Long paymentId, Long paymentCancellationId, PaymentLedgerType paymentLedgerType);


    Optional<PaymentLedger> findByTransactionKeyAndType(String transactionKey, PaymentLedgerType paymentLedgerType);

    @Query("""
            select pl from PaymentLedger pl
            where pl.occurredAt >= :start and pl.occurredAt < :end
            order by pl.id
            """)
    List<PaymentLedger> findLedgerByTargetDate(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
