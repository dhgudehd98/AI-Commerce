package com.sh.aicommerce.payment.dto.request;


import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PaymentBillingCardRequestDto {
    private Long memberId;
    private Long paymentId;
    private Long attemptId;
    private Long orderId;
    private Long cardId;
    private String cardBillingKey;
    private String customerKey;
    private Integer amount;
    private String orderNumber;
    private String orderName;
    private String customerEmail;
    private String customerName;
    private Integer taxFreeAmount = 0;
    private String idempotencyKey;
    private PaymentAction action;

    public PaymentBillingCardRequestDto(Payment payment, PaymentAttempt attempt, Orders order, Card card, Member member, String orderName) {
        this.memberId = member.getId();
        this.paymentId = payment.getId();
        this.attemptId = attempt.getId();
        this.orderId = order.getId();
        this.cardId = card.getId();
        this.cardBillingKey = card.getBillingKey();
        this.customerKey = member.getCustomerKey();
        this.amount = payment.getAmount();
        this.orderNumber = order.getOrderNumber();
        this.orderName = orderName;
        this.customerEmail = member.getEmail();
        this.customerName = member.getMemberName();
        this.idempotencyKey = attempt.getIdempotencyKey();
    }
}