package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** PG에서 성공이 확인된 승인·전액 취소 사실을 추가만 하는 원장입니다. */
@Entity
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_ledger", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_ledger_payment_type", columnNames = {"payment_id", "type"}),
        @UniqueConstraint(name = "uk_payment_ledger_cancellation", columnNames = "payment_cancellation_id")
})
public class PaymentLedger {

    private static final String SUPPORTED_CURRENCY = "KRW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_cancellation_id", updatable = false)
    private PaymentCancellation paymentCancellation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentLedgerType type;

    @Column(nullable = false, updatable = false)
    private Integer amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String paymentKey;

    @Column(nullable = false, updatable = false)
    private String transactionKey;

    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 호출부에서 PG 승인 성공을 검증한 후 approvedAt을 전달합니다. */
    public static PaymentLedger createApproval(Payment payment, Integer amount, String currency,
                                               String paymentKey, String transactionKey,
                                               OffsetDateTime approvedAt) {
        return create(payment, null, PaymentLedgerType.APPROVAL, amount, currency,
                paymentKey, transactionKey, approvedAt);
    }

    /** 호출부에서 PG 전액 취소 성공을 검증한 후 해당 거래의 canceledAt을 전달합니다. */
    public static PaymentLedger createCancellation(Payment payment, PaymentCancellation cancellation,
                                                   Integer amount, String currency, String paymentKey,
                                                   String transactionKey, OffsetDateTime canceledAt) {
        if (payment == null || payment.getId() == null || cancellation == null || cancellation.getId() == null
                || cancellation.getPayment() == null
                || !Objects.equals(cancellation.getPayment().getId(), payment.getId())
                || !Objects.equals(cancellation.getCancelAmount(), amount)) {
            throw new PaymentException("[결제/LEDGER] 원본 결제와 취소 내역의 연결 또는 금액이 일치하지 않습니다.");
        }
        return create(payment, cancellation, PaymentLedgerType.CANCELLATION, amount, currency,
                paymentKey, transactionKey, canceledAt);
    }

    private static PaymentLedger create(Payment payment, PaymentCancellation cancellation,
                                        PaymentLedgerType type, Integer amount, String currency,
                                        String paymentKey, String transactionKey, OffsetDateTime occurredAt) {
        if (payment == null || payment.getId() == null || amount == null || amount <= 0
                || !Objects.equals(payment.getAmount(), amount)
                || !SUPPORTED_CURRENCY.equals(currency)
                || !StringUtils.hasText(paymentKey) || !StringUtils.hasText(transactionKey)
                || occurredAt == null
                || StringUtils.hasText(payment.getPaymentKey())
                && !Objects.equals(payment.getPaymentKey(), paymentKey)) {
            throw new PaymentException("[결제/LEDGER] 결제·금액·통화·PG 거래 식별자·발생 시각이 유효하지 않습니다.");
        }
        PaymentLedger ledger = new PaymentLedger();
        ledger.payment = payment;
        ledger.paymentCancellation = cancellation;
        ledger.type = type;
        ledger.amount = amount;
        ledger.currency = currency;
        ledger.paymentKey = paymentKey;
        ledger.transactionKey = transactionKey;
        ledger.occurredAt = occurredAt;
        ledger.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return ledger;
    }
}
