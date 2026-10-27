package com.sh.aicommerce.recovery.dto.request;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentAttempt;
import com.sh.aicommerce.entity.PaymentRecovery;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProcessingPaymentRequestDto {
    private Long recoveryId;
    private Long paymentId;
    private Long attemptId;
    private Long orderId;
    private String orderNumber;

    private String failureCode; // 실패 코드에 대한 값은 따로 설정하기 -> 애초에 Exception에 저장할때 설정하기
    private String failureMessage;


    public ProcessingPaymentRequestDto(Payment payment, PaymentAttempt attempt, Orders order, PaymentRecovery recovery) {
        this.recoveryId = recovery.getId();
        this.paymentId = payment.getId();
        this.attemptId = attempt.getId();
        this.orderId = order.getId();
        this.orderNumber = order.getOrderNumber();
        this.failureMessage = payment.getFailReason();
    }
}