package com.sh.aicommerce.batch.transactionHistory.processor;

import com.sh.aicommerce.toss.dto.transaction.TransactionResponseDto;
import org.springframework.batch.item.ItemProcessor;

public class PgTransactionProcessor implements ItemProcessor<TransactionResponseDto, TransactionResponseDto> {
    @Override
    public TransactionResponseDto process(TransactionResponseDto item) throws Exception {
        return null;
    }
}