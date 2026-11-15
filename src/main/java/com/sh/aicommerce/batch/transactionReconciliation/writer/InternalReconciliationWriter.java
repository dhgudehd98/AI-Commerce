package com.sh.aicommerce.batch.transactionReconciliation.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.batch.transactionReconciliation.dto.InternalReconciliationResultDto;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationIssueRepository;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationResultRepository;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@RequiredArgsConstructor
@Slf4j
public class InternalReconciliationWriter implements ItemWriter<InternalReconciliationResultDto> {
    private final Long collectionRunId;
    private final Long reconciliationRunId;
    private final PgTransactionReconciliationRunRepository runRepository;
    private final PgTransactionReconciliationResultRepository resultRepository;
    private final PgTransactionReconciliationIssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Chunk<? extends InternalReconciliationResultDto> chunk) {

        var run = runRepository.findById(reconciliationRunId)
                .orElseThrow(() -> new PaymentException("대사 실행이 없습니다."));

        if (!Objects.equals(run.getCollectionRun().getId(), collectionRunId)) {
            throw new PaymentException("대상 수집 실행이 일치하지 않습니다.");
        }

        for (var item : chunk.getItems()) {
            if (!item.isPgTransactionFound()) {
                saveMissingPgTransaction(run, item.getLedger());
            }
            run.recordInternalResult(item.isPgTransactionFound());
        }

        log.info("[역방향 거래 대사] runId={} checkedCount={} internalOnlyCount={}",
                reconciliationRunId, run.getInternalCheckedCount(), run.getInternalOnlyCount());
    }

    private void saveMissingPgTransaction(PgTransactionReconciliationRun run, PaymentLedger ledger) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ledgerId", ledger.getId());
        snapshot.put("paymentId", ledger.getPayment().getId());
        snapshot.put("type", ledger.getType().name());
        snapshot.put("paymentKey", ledger.getPaymentKey());
        snapshot.put("transactionKey", ledger.getTransactionKey());
        snapshot.put("amount", ledger.getAmount());
        snapshot.put("currency", ledger.getCurrency());
        snapshot.put("occurredAt", ledger.getOccurredAt().toString());

        var result = PgTransactionReconciliationResult.builder()
                .reconciliationRun(run)
                .payment(ledger.getPayment())
                .paymentLedger(ledger)
                .transactionType(switch (ledger.getType()) {
                    case APPROVAL -> PgTransactionReconciliationResult.TransactionType.APPROVAL;
                    case CANCELLATION -> PgTransactionReconciliationResult.TransactionType.CANCELLATION;
                })
                .status(PgTransactionReconciliationResult.Status.MISMATCHED)
                .internalSnapshot(snapshot)
                // PG 거래가 없으므로 pgTransaction, pgSnapshot, amountDiff는 null
                .build();

        result = resultRepository.save(result);

        issueRepository.save(PgTransactionReconciliationIssue.builder()
                .result(result)
                .code(PgTransactionReconciliationIssue.Code.PG_TRANSACTION_NOT_FOUND)
                .fieldName("transactionKey")
                .pgValue(objectMapper.getNodeFactory().nullNode())
                .internalValue(objectMapper.valueToTree(ledger.getTransactionKey()))
                .description("내부 원장에 대응하는 거래가 선택된 PG 수집 실행에 없습니다.")
                .build());
    }
}
