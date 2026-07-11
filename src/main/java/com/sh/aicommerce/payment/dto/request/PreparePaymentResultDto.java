package com.sh.aicommerce.payment.dto.request;

import com.sh.aicommerce.enums.payment.Bank;
import com.sh.aicommerce.enums.payment.CardCompany;
import com.sh.aicommerce.enums.payment.GeneralPayment;
import com.sh.aicommerce.enums.payment.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentResultDto {
    private Long orderId;
    private Long paymentId;

    private Integer amount;
    private PaymentMethod paymentMethod;

    private GeneralPayment generalPayment;
    private CardCompany cardCompany;

    private Long savedCardId;
    private Long savedAccountId;
}
