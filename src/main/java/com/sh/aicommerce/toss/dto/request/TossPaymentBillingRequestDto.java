package com.sh.aicommerce.toss.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentBillingRequestDto {
    private String customerKey;
    private String authKey;
}