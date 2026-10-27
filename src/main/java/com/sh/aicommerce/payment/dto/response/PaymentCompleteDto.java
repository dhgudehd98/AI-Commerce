package com.sh.aicommerce.payment.dto.response;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentAttempt;
import com.sh.aicommerce.enums.payment.PaymentAction;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCompleteDto {
    private Long paymentId;
    private Long orderId;
    private Long attemptId;

    private String orderNumber;
    private String orderName; // 결제 상품
    private Integer totalAmount; // 결제 금액
    private PaymentStatus paymentStatus;
    private PaymentAction action; // 결제 요청 멱등성 판단을 위한 Action 값 추가
    private LocalDateTime paidAt;

    public PaymentCompleteDto(Payment payment, Orders order, PaymentAttempt attempt) {
        this.paymentId = payment.getId();
        this.orderId = order.getId();
        this.attemptId = attempt.getId();

        this.orderNumber = order.getOrderNumber();
        this.orderName = order.getOrderItems().stream()
                .findFirst()
                .map(orderItem -> orderItem.getProductNameSnapshot())
                .get();
        this.totalAmount = payment.getAmount();
        this.paymentStatus = payment.getStatus();
        this.action = PaymentAction.COMPLETED;
        this.paidAt = payment.getPaidAt();
    }
}