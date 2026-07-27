package com.sh.aicommerce.batch.writer;

import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.ProductInventory;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.service.PaymentTransactionService;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@RequiredArgsConstructor
public class ExpiredPaymentWriter implements ItemWriter<Long> {

    private final PaymentTransactionService transactionService;
    private final LocalDateTime expiredAt;

    @Override
    public void write(Chunk<? extends Long> chunk) throws Exception {
        for (Long paymentId : chunk) {
            transactionService.payFailOrExpiredPayment(paymentId, expiredAt);
        }
    }
}