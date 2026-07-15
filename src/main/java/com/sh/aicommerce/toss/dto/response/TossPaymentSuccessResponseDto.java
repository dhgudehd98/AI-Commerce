package com.sh.aicommerce.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentSuccessResponseDto {

    private String mId;
    private String lastTransactionKey;
    private String paymentKey;
    private String orderId;
    private String orderName;
    private String status;
    private String requestedAt;
    private String approvedAt;
    private String method;
    private String currency;

    private Integer totalAmount;
    private Integer balanceAmount;
    private Integer suppliedAmount;
    private Integer vat;
    private Integer taxFreeAmount;

    private Boolean isPartialCancelable;

    private CardResponse card;
    private EasyPayResponse easyPay;
    private ReceiptResponse receipt; // 토스페이먼츠 결제 내역 영수증
    private CheckoutResponse checkout; // 응답 결과 내역 확인

    @Override
    public String toString() {
        return "TossPaymentSuccessResponseDto{" +
                "mId='" + mId + '\'' +
                ", lastTransactionKey='" + lastTransactionKey + '\'' +
                ", paymentKey='" + paymentKey + '\'' +
                ", orderId='" + orderId + '\'' +
                ", orderName='" + orderName + '\'' +
                ", status='" + status + '\'' +
                ", requestedAt='" + requestedAt + '\'' +
                ", approvedAt='" + approvedAt + '\'' +
                ", method='" + method + '\'' +
                ", currency='" + currency + '\'' +
                ", totalAmount=" + totalAmount +
                ", balanceAmount=" + balanceAmount +
                ", suppliedAmount=" + suppliedAmount +
                ", vat=" + vat +
                ", taxFreeAmount=" + taxFreeAmount +
                ", isPartialCancelable=" + isPartialCancelable +
                ", card=" + card +
                ", easyPay=" + easyPay +
                ", receipt=" + receipt.getUrl() +
                ", checkout=" + checkout.getUrl() +
                '}';
    }
}
