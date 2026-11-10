package com.sh.aicommerce.entity;

import com.sh.aicommerce.toss.dto.transaction.TransactionResponseDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** PG에서 수집한 거래 사실. 내부 Payment가 없는 거래도 보관하기 위해 FK를 두지 않습니다. */
@Entity
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pg_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_pg_transaction_mid_key",
                columnNames = {"m_id", "transaction_key"}),
        indexes = {
                @Index(name = "idx_pg_transaction_at", columnList = "transaction_at"),
                @Index(name = "idx_pg_transaction_payment_key", columnList = "payment_key")
        })
public class PgTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "m_id", nullable = false, updatable = false)
    private String mId; // PG 상점 아이디. 거래 키와 함께 중복 수집 방지 기준

    @Column(name = "transaction_key", nullable = false, updatable = false, length = 64)
    private String transactionKey; // 승인·취소 각각의 거래 식별자

    @Column(name = "payment_key", nullable = false, updatable = false, length = 200)
    private String paymentKey; // 하나의 결제에 여러 승인·취소 거래가 연결되므로 UNIQUE 아님

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId; // 내부 주문 PK가 아닌, PG에 전달한 주문번호

    @Column(nullable = false, updatable = false, length = 30)
    private String status; // 수집한 거래의 PG 상태. 내부 PaymentStatus와 구분

    @Column(nullable = false, updatable = false)
    private Long amount; // 해당 거래의 금액

    @Column(nullable = false, updatable = false, length = 10)
    private String currency; // PG 응답의 통화

    @Column(updatable = false, length = 50)
    private String method; // 결제 수단

    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(name = "transaction_at", nullable = false, updatable = false)
    private OffsetDateTime transactionAt; // PG 거래 발생 시각. 수집 시각과 구분

    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false, updatable = false)
    private OffsetDateTime collectedAt; // 우리 서버에서 수집한 시각

    @Column(updatable = false)
    private Long collectionRunId; // PgTransactionCollectionRun의 ID. 기존 수집 데이터는 null일 수 있음

    public PgTransaction(TransactionResponseDto response, Long collectionRunId) {
        this.mId = response.getMId();
        this.transactionKey = response.getTransactionKey();
        this.paymentKey = response.getPaymentKey();
        this.orderId = response.getOrderId();
        this.status = response.getStatus();
        this.amount = response.getAmount();
        this.currency = response.getCurrency();
        this.method = response.getMethod();
        this.transactionAt = response.getTransactionAt();
        this.collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.collectionRunId = collectionRunId;
    }
}
