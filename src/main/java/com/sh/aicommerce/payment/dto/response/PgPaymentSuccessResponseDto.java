package com.sh.aicommerce.payment.dto.response;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentAttempt;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PgPaymentSuccessResponseDto {

    private Long paymentId;
    private String orderNumber;

    private PaymentStatus paymentStatus;
    private String orderName;
    private Integer totalAmount;
    private LocalDateTime paidAt;

    public PgPaymentSuccessResponseDto(Payment payment, PaymentAttempt attempt, Orders order) {
        this.paymentId = payment.getId();
        this.orderNumber = order.getOrderNumber();

        this.paymentStatus = payment.getStatus();
        this.orderName = order.getOrderItems().stream()
                .findFirst()
                .map(orderItem -> orderItem.getProductNameSnapshot())
                .get();
        this.totalAmount = payment.getAmount();
        this.paidAt = payment.getPaidAt();
    }
}