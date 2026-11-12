package com.sh.aicommerce.admin.batch.dto.response;

import com.sh.aicommerce.entity.PgTransactionReconciliationResult;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
public class AdminMismatchResponseDto {
    private final Long id; // 거래별 검사 결과 ID
    private final Long pgTransactionId;
    private final Long paymentId; // PG에만 있는 거래는 null
    private final Long paymentLedgerId;
    private final String orderNumber; // PG 원본 주문번호
    private final String paymentKey;
    private final String transactionKey;
    private final PgTransactionReconciliationResult.TransactionType transactionType;
    private final Long pgAmount;
    private final String currency;
    private final Long amountDiff; // PG - 원장, 원장 누락이면 null
    private final OffsetDateTime checkedAt;
    private List<AdminMismatchIssueResponseDto> issues = List.of();

    public AdminMismatchResponseDto(Long id, Long pgTransactionId, Long paymentId, Long paymentLedgerId,
                                    String orderNumber, String paymentKey, String transactionKey,
                                    PgTransactionReconciliationResult.TransactionType transactionType,
                                    Long pgAmount, String currency, Long amountDiff, OffsetDateTime checkedAt) {
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
