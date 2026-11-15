package com.sh.aicommerce.admin.batch.dto.response;

import com.sh.aicommerce.entity.PgTransactionReconciliationResult;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
public class AdminReconciliationResultResponseDto {
    private final Long id; // 거래별 검사 결과 ID
    private final Long pgTransactionId; // 역방향 PG 거래 누락이면 null
    private final Long paymentId; // PG에만 있는 거래는 null
    private final Long paymentLedgerId;
    private final String orderNumber; // PG 원본 주문번호, PG 거래 누락이면 내부 주문번호
    private final String paymentKey;
    private final String transactionKey;
    private final PgTransactionReconciliationResult.TransactionType transactionType;
    private final Long pgAmount; // PG 거래 누락이면 null. 내부 원장 금액으로 대체하지 않음
    private final String currency;
    private final Long amountDiff; // PG - 원장, 원장 누락이면 null
    private final OffsetDateTime checkedAt;
    private final PgTransactionReconciliationResult.Status status;
    private final String exclusionCode;
    private final String exclusionReason;
    private final java.util.Map<String, Object> pgSnapshot;
    private final java.util.Map<String, Object> internalSnapshot;
    private List<AdminMismatchIssueResponseDto> issues = List.of();

    public AdminReconciliationResultResponseDto(Long id, Long pgTransactionId, Long paymentId, Long paymentLedgerId,
                                    String orderNumber, String paymentKey, String transactionKey,
                                    PgTransactionReconciliationResult.TransactionType transactionType,
                                    Long pgAmount, String currency, Long amountDiff, OffsetDateTime checkedAt,
                                    PgTransactionReconciliationResult.Status status, String exclusionCode,
                                    String exclusionReason, java.util.Map<String, Object> pgSnapshot,
                                    java.util.Map<String, Object> internalSnapshot) {
        this.status = status;
        this.exclusionCode = exclusionCode;
        this.exclusionReason = exclusionReason;
        this.pgSnapshot = pgSnapshot;
        this.internalSnapshot = internalSnapshot;
        this.id = id;
        this.pgTransactionId = pgTransactionId;
        this.paymentId = paymentId;
        this.paymentLedgerId = paymentLedgerId;
        this.orderNumber = orderNumber;
        this.paymentKey = paymentKey;
        this.transactionKey = transactionKey;
        this.transactionType = transactionType;
        this.pgAmount = pgAmount;
        this.currency = currency;
        this.amountDiff = amountDiff;
        this.checkedAt = checkedAt;
    }

    public void attachIssues(List<AdminMismatchIssueResponseDto> issues) {
        this.issues = List.copyOf(issues);
    }
}
