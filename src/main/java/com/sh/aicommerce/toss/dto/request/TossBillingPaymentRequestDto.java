package com.sh.aicommerce.toss.dto.request;

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
}