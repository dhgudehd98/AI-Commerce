package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** PG 거래 한 건의 검사 결과. 정상 거래도 저장합니다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "pg_transaction_reconciliation_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recon_result_run_transaction",
                columnNames = {"reconciliation_run_id", "pg_transaction_id"})
})
public class PgTransactionReconciliationResult {
    public enum Status { MATCHED, MISMATCHED, EXCLUDED }
    public enum TransactionType { APPROVAL, CANCELLATION, UNSUPPORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 관리자 화면·AI 보고서의 근거 ID

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false, updatable = false)
    private PgTransactionReconciliationRun reconciliationRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pg_transaction_id", nullable = false, updatable = false)
    private PgTransaction pgTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", updatable = false)
    private Payment payment; // PG에만 있는 거래라면 null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_ledger_id", updatable = false)
    private PaymentLedger paymentLedger; // 원장이 누락됐다면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType transactionType;

    @Column(updatable = false, length = 100)
    private String exclusionCode; // EXCLUDED 사유 코드. 불일치와 구분

    @Column(updatable = false, length = 500)
    private String exclusionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "json")
    private Map<String, Object> pgSnapshot; // 검사 당시 식별자·상태·금액·통화·거래 시각

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "json")
    private Map<String, Object> internalSnapshot; // 검사 당시 내부 값. 내부 거래가 없으면 null

    @Column(updatable = false)
    private Long amountDiff; // PG 금액 - 연결된 원장 금액. 원장 누락이면 null

    @Builder.Default
    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false, updatable = false)
    private OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
