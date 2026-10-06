package com.sh.aicommerce.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** PG 결제 조회 응답. 결제수단별 객체와 취소 이력은 없으면 null이다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentRequestHistoryResponseDto {
    private String version;
    private String paymentKey;
    private String orderId;
    private String orderName;
    @JsonProperty("mId")
    private String mId;
    private String type; // NORMAL, BILLING, BRANDPAY
    private String method;
    private String status;
    private String lastTransactionKey;
    private String currency;
    private String country;

    private Integer totalAmount; // 최초 결제 금액
    private Integer balanceAmount; // 취소 가능한 잔액
    private Integer suppliedAmount;
    private Integer vat;
    private Integer taxFreeAmount;
    private Integer taxExemptionAmount;

    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private Boolean useEscrow;
    private Boolean cultureExpense;
    @JsonProperty("isPartialCancelable")
    private Boolean isPartialCancelable;

    private CardResponse card;
    private EasyPayResponse easyPay;
    private TransferResponse transfer;
    private VirtualAccountResponse virtualAccount;
    private MobilePhoneResponse mobilePhone;
    private GiftCertificateResponse giftCertificate;
    private List<CancelResponseDto> cancels;

    private ReceiptResponse receipt;
    private CheckoutResponse checkout;
    private DiscountResponse discount;
    private CashReceiptResponse cashReceipt;
    private List<CashReceiptHistoryResponse> cashReceipts;
    private TossPaymentErrorResponseDto failure; // 결제 승인 실패 정보
    private Map<String, String> metadata;

    // 웹훅 검증용 secret과 deprecated 필드는 매핑하지 않는다.

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransferResponse {
        private String bankCode;
        private String settlementStatus;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VirtualAccountResponse {
        private String accountType;
        private String accountNumber;
        private String bankCode;
        private String customerName;
        private String depositorName;
        // 문서상 오프셋 없는 날짜 형식이므로 원문을 보존한다.
        private String dueDate;
        private String refundStatus;
        private Boolean expired;
        private String settlementStatus;
        private RefundReceiveAccountResponse refundReceiveAccount;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RefundReceiveAccountResponse {
        private String bankCode;
        private String accountNumber;
        private String holderName;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MobilePhoneResponse {
        private String customerMobilePhone;
        private String settlementStatus;
        private String receiptUrl;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GiftCertificateResponse {
        private String approveNo;
        private String settlementStatus;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscountResponse {
        private Integer amount;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CashReceiptResponse {
        private String type;
        private String receiptKey;
        private String issueNumber;
        private String receiptUrl;
        private Integer amount;
        private Integer taxFreeAmount;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CashReceiptHistoryResponse {
        private String receiptKey;
        private String orderId;
        private String orderName;
        private String type;
        private String issueNumber;
        private String receiptUrl;
        private String businessNumber;
        private String transactionType;
        private Integer amount;
        private Integer taxFreeAmount;
        private String issueStatus;
        private TossPaymentErrorResponseDto failure;
        private String customerIdentityNumber;
        private OffsetDateTime requestedAt;
    }
}
