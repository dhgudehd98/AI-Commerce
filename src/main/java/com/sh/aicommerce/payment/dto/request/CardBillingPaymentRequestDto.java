package com.sh.aicommerce.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CardBillingPaymentRequestDto {

    @NotBlank
    private String orderNumber;

    @NotNull
    @Positive
    private Long paymentId;

    @NotNull
    @Positive
    private Long paymentAttemptId;
}
