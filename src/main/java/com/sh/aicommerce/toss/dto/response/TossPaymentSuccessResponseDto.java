package com.sh.aicommerce.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.enums.payment.PaymentStatus;
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


    public TossPaymentSuccessResponseDto(Payment payment) {
        this.paymentKey = payment.getPaymentKey();
        this.orderId = payment.getOrder().getOrderNumber();
        this.status = payment.getStatus() == PaymentStatus.PAID ? "DONE" : payment.getStatus().name();
        this.requestedAt = payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : null;
        this.approvedAt = payment.getPaidAt() != null ? payment.getPaidAt().toString() : null;
        this.method = payment.getProvider();
        this.currency = "KRW";
        this.totalAmount = payment.getAmount();
        this.balanceAmount = payment.getAmount();
        this.taxFreeAmount = 0;

        if (payment.getCardCode() != null || payment.getApprovedNumber() != null) {
            this.card = new CardResponse(payment);
        }
    }

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
                ", receiptUrl=" + (receipt != null ? receipt.getUrl() : null) +
                ", checkoutUrl=" + (checkout != null ? checkout.getUrl() : null) +
                '}';
    }
}
