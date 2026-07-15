package com.sh.aicommerce.toss.dto.response;

import lombok.Getter;

@Getter
public class CardResponse {

    private String issuerCode;
    private String acquirerCode;
    private String number;
    private Integer installmentPlanMonths;
    private Boolean isInterestFree;
    private String interestPayer;
    private String approveNo;
    private Boolean useCardPoint;
    private String cardType;
    private String ownerType;
    private String acquireStatus;
    private Long amount;
}