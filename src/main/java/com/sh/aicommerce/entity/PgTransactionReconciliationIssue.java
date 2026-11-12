package com.sh.aicommerce.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 거래 결과 하나에 여러 불일치 사유가 연결될 수 있습니다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "pg_transaction_reconciliation_issue", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recon_issue_result_code",
                columnNames = {"result_id", "code"})
})
public class PgTransactionReconciliationIssue {
    public enum Code {
        PAYMENT_NOT_FOUND,               // 내부 결제 없음
        ORDER_NUMBER_MISMATCH,            // PG 주문번호와 Payment 주문번호 불일치
        PAYMENT_AMOUNT_MISMATCH,          // PG 승인 금액과 Payment 금액 불일치

        CANCELLATION_NOT_FOUND,           // 내부 취소 내역 없음
        CANCELLATION_PAYMENT_MISMATCH,    // 취소 내역과 원본 Payment 연결 불일치
        CANCELLATION_ORDER_NUMBER_MISMATCH, // PG 주문번호와 취소 내역의 주문번호 불일치
        CANCELLATION_AMOUNT_MISMATCH,     // PG 취소 금액과 내부 취소 금액 불일치

        APPROVAL_LEDGER_NOT_FOUND,                // 승인 원장 없음
        APPROVAL_LEDGER_PAYMENT_MISMATCH,         // 승인 원장의 Payment 연결 불일치
        APPROVAL_LEDGER_PAYMENT_KEY_MISMATCH,     // 승인 원장의 결제 키 불일치
        APPROVAL_LEDGER_TRANSACTION_KEY_MISMATCH, // 승인 원장의 승인 거래 키 불일치
        APPROVAL_LEDGER_AMOUNT_MISMATCH,          // 승인 원장의 금액 불일치
        APPROVAL_LEDGER_CURRENCY_MISMATCH,        // 승인 원장의 통화 불일치

        CANCELLATION_LEDGER_NOT_FOUND,                // 취소 원장 없음
        CANCELLATION_LEDGER_PAYMENT_MISMATCH,         // 취소 원장의 Payment 연결 불일치
        CANCELLATION_LEDGER_CANCELLATION_MISMATCH,    // 취소 원장의 PaymentCancellation 연결 불일치
        CANCELLATION_LEDGER_PAYMENT_KEY_MISMATCH,     // 취소 원장의 결제 키 불일치
        CANCELLATION_LEDGER_TRANSACTION_KEY_MISMATCH, // 취소 원장의 취소 거래 키 불일치
        CANCELLATION_LEDGER_AMOUNT_MISMATCH,          // 취소 원장의 금액 불일치
        CANCELLATION_LEDGER_CURRENCY_MISMATCH         // 취소 원장의 통화 불일치
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false, updatable = false)
    private PgTransactionReconciliationResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private Code code; // 집계 및 AI 보고서 분류 기준

    @Column(nullable = false, updatable = false, length = 100)
    private String fieldName; // 비교한 필드. amount, currency, transactionKey 등

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "json")
    private JsonNode pgValue; // PG에서 저장된 값

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "json")
    private JsonNode internalValue; // DB에 저장되어 있는 내부 데이터 값 => 내부 데이터가 없으면 null

    @Column(updatable = false, length = 500)
    private String description; // 화면 표시용 설명. 집계는 code 기준

    public void saveResult(PgTransactionReconciliationResult reconciliationResult) {
        this.result = reconciliationResult;
    }
}
