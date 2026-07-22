package com.sh.aicommerce.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sh.aicommerce.enums.payment.Bank;
import com.sh.aicommerce.enums.payment.CardCompany;

import com.sh.aicommerce.enums.payment.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentResultDto {
    private Long orderId;
    private String orderNumber;
    private String productName;
    private Long paymentId;

    private Integer amount;
    private PaymentMethod paymentMethod;
    private CardCompany cardCompany;

    private Long savedCardId;
    private Long savedAccountId;

    @JsonIgnore
    private String billingKey;

    @JsonIgnore
    private String customerKey;

}
