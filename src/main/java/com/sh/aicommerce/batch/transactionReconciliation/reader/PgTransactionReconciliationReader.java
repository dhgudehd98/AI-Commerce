package com.sh.aicommerce.batch.transactionReconciliation.reader;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.PgTransaction;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class PgTransactionReconciliationReader implements ItemReader<PgTransaction> {

    private final Long collectionRunId;
    private final PgTransactionRepository transactionRepository;
    private Iterator<PgTransaction> transactions = Collections.emptyIterator();
    private boolean initialized;


    @Override
    public PgTransaction read() {
        if (!initialized) {
            initialize();
            initialized = true;
        }

        // 전체 거래를 반환했거나 정상 수집 결과가 0건이면 종료
        return transactions.hasNext() ? transactions.next() : null;
    }

    private void initialize() {

        if (collectionRunId == null) {
            throw new PaymentException("대사 대상 수집 실행 ID가 없습니다.");
        }

        List<PgTransaction> items = transactionRepository
                .findByCollectionRunIdOrderByIdAsc(collectionRunId);

        transactions = items.iterator();

        log.info(
                "[PG 거래 대사 조회] collectionRunId={} count={}",
                collectionRunId, items.size()
        );
    }
}