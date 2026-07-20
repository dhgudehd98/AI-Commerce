package com.sh.aicommerce.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class TossPaymentBillingResponseDto {

    @JsonProperty("mId")
    private String mId;

    private String customerKey;

    private OffsetDateTime authenticatedAt;

    private String method; // 예: "카드"

    private String billingKey;

    private Card card;

    private String cardCompany;

    private String cardNumber;

    private Object transfers; // 계좌이체 관련 - 카드 발급 시 null

    private Object easyPay;   // 간편결제 관련 - 카드 발급 시 null

    @Getter
    @NoArgsConstructor
    public static class Card {

        private String issuerCode;

        private String acquirerCode;

        private String number;

        private String cardType; // "신용" / "체크"

        private String ownerType; // "개인" / "법인"
    }

    @Override
    public String toString() {
        return "TossPaymentBillingResponseDto{" +
                "mId='" + mId + '\'' +
                ", customerKey='" + customerKey + '\'' +
                ", authenticatedAt=" + authenticatedAt +
                ", method='" + method + '\'' +
                ", billingKey='" + billingKey + '\'' +
                ", issuerCode=" + card.issuerCode + '\'' +
                ", acquireCode=" + card.acquirerCode + '\'' +
                ", cardNumber=" + card.number + '\'' +
                ", cardType=" + card.cardType + '\'' +
                ", ownerType='" + card.ownerType + '\'' +
                ", cardNumber='" + cardNumber + '\'' +
                ", transfers=" + transfers +
                ", easyPay=" + easyPay +
                '}';
    }
}