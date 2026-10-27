package com.sh.aicommerce.toss.dto.request;

import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossBillingPaymentRequestDto {
    private String customerKey;
    private Integer amount;
    private String orderId;
    private String orderName;
    private String customerEmail;
    private String customerName;
    private Integer taxFreeAmount;

    public TossBillingPaymentRequestDto(PaymentBillingCardRequestDto billingCardRequestDto) {
        this.customerKey = billingCardRequestDto.getCustomerKey();
        this.amount = billingCardRequestDto.getAmount();
        this.orderId = billingCardRequestDto.getOrderNumber();
        this.orderName = billingCardRequestDto.getOrderName();
        this.customerEmail = billingCardRequestDto.getCustomerEmail();
        this.customerName = billingCardRequestDto.getCustomerName();
        this.taxFreeAmount = billingCardRequestDto.getTaxFreeAmount();
    }
}