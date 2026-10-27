package com.sh.aicommerce.batch.writer;

import com.sh.aicommerce.payment.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.LocalDateTime;


@RequiredArgsConstructor
public class ExpiredPaymentWriter implements ItemWriter<Long> {

    private final PaymentTransactionService transactionService;
    private final LocalDateTime expiredAt;

    @Override
    public void write(Chunk<? extends Long> chunk) throws Exception {
        for (Long paymentId : chunk) {
//            transactionService.payFailOrExpiredPayment(paymentId, expiredAt);
        }
    }
}