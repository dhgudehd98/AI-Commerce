package com.sh.aicommerce.batch.transactionReconciliation.dto;

import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.entity.PgTransaction;

public record InternalReconciliationItem(
        PaymentLedger ledger,
        PgTransaction pgTransaction
) {
}
