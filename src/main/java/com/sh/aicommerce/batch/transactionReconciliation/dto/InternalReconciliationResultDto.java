package com.sh.aicommerce.batch.transactionReconciliation.dto;

import com.sh.aicommerce.entity.PaymentLedger;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InternalReconciliationResultDto {
    private final PaymentLedger ledger;
    private final boolean pgTransactionFound;
}
