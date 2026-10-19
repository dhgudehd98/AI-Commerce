package com.sh.aicommerce.payment.dto.response;

import com.sh.aicommerce.enums.payment.PaymentAction;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCardResultDto {

    private Long paymentId;
    private String orderNumber;

    private PaymentStatus paymentStatus;
    private String orderName; // 결제 상품
    private Integer totalAmount; // 결제 금액
    private LocalDateTime paidAt;


    public PaymentCardResultDto(PgPaymentSuccessResponseDto responseDto, Long paymentId) {
        this.paymentId = paymentId;
        this.orderNumber = responseDto.getOrderNumber();

        this.paymentStatus = PaymentStatus.PAID;
        this.orderName = responseDto.getOrderName();
        this.totalAmount = responseDto.getTotalAmount();
    }

    public PaymentCardResultDto(PaymentCompleteDto completeDto) {
        this.paymentId = completeDto.getPaymentId();
        this.orderNumber = completeDto.getOrderNumber();

        this.paymentStatus = completeDto.getPaymentStatus();
        this.orderName = completeDto.getOrderName();
        this.totalAmount = completeDto.getTotalAmount();
        this.paidAt = completeDto.getPaidAt();
    }
}