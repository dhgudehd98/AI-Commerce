package com.sh.aicommerce.batch.transactionHistory.writer;

import com.sh.aicommerce.entity.PgTransaction;
import com.sh.aicommerce.toss.dto.transaction.TransactionResponseDto;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;

@RequiredArgsConstructor
@Slf4j
public class PgTransactionWriter implements ItemWriter<TransactionResponseDto> {

    private final PgTransactionRepository transactionRepository;
    private final Long collectionRunId;

    @Override
    public void write(Chunk<? extends TransactionResponseDto> chunk) throws Exception {

        if (collectionRunId == null) throw new TossPaymentException("수집 실행 ID가 없습니다.");
        List<PgTransaction> transactions = new ArrayList<>();
        for (TransactionResponseDto dto : chunk.getItems()) {
            PgTransaction existing = transactionRepository
                    .findByMIdAndTransactionKey(dto.getMId(), dto.getTransactionKey()).orElse(null);
            if (existing != null) {
                // 실패한 실행을 처음부터 재조회해도 이미 커밋된 동일 거래는 다시 저장하지 않습니다.
                if (!Objects.equals(existing.getCollectionRunId(), collectionRunId)
                        || !Objects.equals(existing.getPaymentKey(), dto.getPaymentKey())
                        || !Objects.equals(existing.getOrderId(), dto.getOrderId())
                        || !Objects.equals(existing.getStatus(), dto.getStatus())
                        || !Objects.equals(existing.getAmount(), dto.getAmount())
                        || !Objects.equals(existing.getCurrency(), dto.getCurrency())
                        || !existing.getTransactionAt().toInstant().equals(dto.getTransactionAt().toInstant())) {
                    throw new TossPaymentException("기존 PG 거래의 수집 실행 또는 거래 내용이 일치하지 않습니다.");
                }
                continue;
            }
            transactions.add(new PgTransaction(dto, collectionRunId));
        }

        transactionRepository.saveAll(transactions);

        log.info("[PG 거래 수집] DB 저장 요청 count={}", transactions.size());

    }
}