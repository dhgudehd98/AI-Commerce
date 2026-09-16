package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelPaymentResponseDto {
    private Long paymentId;
    private String paymentStatus;
    private String paymentMethodLabel;
    private Integer paidAmount;
    private LocalDateTime paidAt;
    private String refundStatus;
    private Integer refundedAmount;
    private LocalDateTime refundedAt;

    public OrderCancelPaymentResponseDto(Payment payment) {
        this.paymentId = payment.getId();
        this.paymentStatus = payment.getStatus().getStatus();
        this.paymentMethodLabel = paymentMethodLabel;
        this.paidAmount = payment.getAmount();
        this.paidAt = payment.getPaidAt();

        //! refund에 대한 부분은 나중에 처리
        this.refundStatus = null;
        this.refundedAmount = null;
        this.refundedAt = null;
    }
}