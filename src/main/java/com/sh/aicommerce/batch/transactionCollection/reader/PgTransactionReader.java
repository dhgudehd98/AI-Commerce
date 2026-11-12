package com.sh.aicommerce.batch.transactionCollection.reader;

import com.sh.aicommerce.toss.dto.transaction.TransactionHistoryResponseDto;
import com.sh.aicommerce.toss.dto.transaction.TransactionResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


@RequiredArgsConstructor
@Slf4j
public class PgTransactionReader implements ItemReader<TransactionResponseDto> {

    private static final int PAGE_SIZE = 100;

    private final TossPaymentClient client;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    private Iterator<TransactionResponseDto> transactions =
            Collections.emptyIterator();

    private String startingAfter;
    private boolean finished;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public TransactionResponseDto read() {
        // 보관 중인 페이지에 거래가 남아 있다면 한 건 반환
        if (transactions.hasNext()) {
            return transactions.next();
        }

        if (finished) {
            return null;
        }

        // 현재 페이지를 모두 반환했을 때만 다음 페이지 조회
        TransactionHistoryResponseDto response =
                client.transactionHistory(
                        startDate,
                        endDate,
                        startingAfter,
                        PAGE_SIZE
                );

        List<TransactionResponseDto> items = response.getTransactions();

        log.info("[PG 거래 수집] startDate={} endDate={} count={}",
                startDate, endDate, items.size());

        if (items.isEmpty()) {
            finished = true;
            return null;
        }

        startingAfter = items.get(items.size() - 1).getTransactionKey();
        transactions = items.iterator();

        return transactions.next();
    }
}