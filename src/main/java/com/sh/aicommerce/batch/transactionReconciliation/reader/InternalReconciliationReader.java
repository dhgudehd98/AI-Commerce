package com.sh.aicommerce.batch.transactionReconciliation.reader;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Iterator;

@RequiredArgsConstructor
@Slf4j
public class InternalReconciliationReader implements ItemReader<PaymentLedger> {

    private final String targetDate;
    private final PaymentLedgerRepository ledgerRepository;
    private Iterator<PaymentLedger> transactions = Collections.emptyIterator();
    private boolean initialized;


    @Override
    public PaymentLedger read() {
        if (!initialized) {
            initialize();
            initialized = true;
        }
        return transactions.hasNext() ? transactions.next() : null;
    }

    private void initialize() {
        LocalDate date;

        try {
            date = LocalDate.parse(targetDate);
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw new PaymentException("대사 대상일은 yyyy-MM-dd 형식이어야 합니다.");
        }
        ZoneId zone = java.time.ZoneId.of("Asia/Seoul");
        OffsetDateTime start = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        transactions = ledgerRepository.findLedgerByTargetDate(start, end).iterator();
    }
}
