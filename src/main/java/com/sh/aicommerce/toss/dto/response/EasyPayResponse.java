package com.sh.aicommerce.toss.dto.response;

import lombok.Getter;

@Getter
public class EasyPayResponse {
    private String provider;
    private Long amount;
    private Long discountAmount;
}