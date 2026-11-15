package com.sh.aicommerce.batch.transactionReconciliation.processor;

import com.sh.aicommerce.batch.transactionReconciliation.dto.InternalReconciliationResultDto;
import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@RequiredArgsConstructor
@Slf4j
public class InternalReconciliationProcessor implements ItemProcessor<PaymentLedger, InternalReconciliationResultDto> {

    private final Long collectionRunId;
    private final PgTransactionRepository transactionRepository;
    @Override
    public InternalReconciliationResultDto process(PaymentLedger ledger) {
        boolean exists = transactionRepository.existsByCollectionRunIdAndTransactionKey(
                collectionRunId, ledger.getTransactionKey());
        return new InternalReconciliationResultDto(ledger, exists);
    }
}
