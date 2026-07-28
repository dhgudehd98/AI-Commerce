package com.sh.aicommerce.entity;

import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TossPaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "toss_payment_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String mid;

    private String lastTransactionKey;

    @Column(nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private String orderNumber;

    private String orderName;

    private String tossStatus;

    @Column(nullable = false)
    private String requestType;

    private String method;

    private String easyPayProvider;

    private String cardIssuerCode;
    private String cardAcquirerCode;
    private String cardNumber;
    private String cardApprovedNumber;
    private String cardType;
    private String ownerType;
    private String acquireStatus;
    private Integer installmentPlanMonths;
    private Boolean interestFree;
    private Boolean useCardPoint;

    private Integer amount;
    private Integer balanceAmount;
    private Integer suppliedAmount;
    private Integer vat;
    private Integer taxFreeAmount;

    private String currency;

    private Boolean partialCancelable;

    private Integer httpStatus;

    private String failureCode;

    private String failureMessage;

    private String receiptUrl;

    private String checkoutUrl;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String responsePayload;

    private LocalDateTime requestedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;

    public static TossPaymentLog confirmSuccess(
            Payment payment,
            TossPaymentSuccessResponseDto response,
            Integer httpStatus,
            String requestPayload,
            String responsePayload
    ) {
        TossPaymentLog log = new TossPaymentLog();
        log.payment = payment;
        log.mid = response.getMId();
        log.lastTransactionKey = response.getLastTransactionKey();
        log.paymentKey = response.getPaymentKey();
        log.orderNumber = response.getOrderId();
        log.orderName = response.getOrderName();
        log.tossStatus = response.getStatus();
        log.requestType = "CONFIRM";
        log.method = response.getMethod();

        if (response.getEasyPay() != null) {
            log.easyPayProvider = response.getEasyPay().getProvider();
        }

        if (response.getCard() != null) {
            log.cardIssuerCode = response.getCard().getIssuerCode();
            log.cardAcquirerCode = response.getCard().getAcquirerCode();
            log.cardNumber = response.getCard().getNumber();
            log.cardApprovedNumber = response.getCard().getApproveNo();
            log.cardType = response.getCard().getCardType();
            log.ownerType = response.getCard().getOwnerType();
            log.acquireStatus = response.getCard().getAcquireStatus();
            log.installmentPlanMonths = response.getCard().getInstallmentPlanMonths();
            log.interestFree = response.getCard().getIsInterestFree();
            log.useCardPoint = response.getCard().getUseCardPoint();
        }

        log.amount = response.getTotalAmount();
        log.balanceAmount = response.getBalanceAmount();
        log.suppliedAmount = response.getSuppliedAmount();
        log.vat = response.getVat();
        log.taxFreeAmount = response.getTaxFreeAmount();
        log.currency = response.getCurrency();
        log.partialCancelable = response.getIsPartialCancelable();
        log.httpStatus = httpStatus;
        if (response.getReceipt() != null) {
            log.receiptUrl = response.getReceipt().getUrl();
        }

        if (response.getCheckout() != null) {
            log.checkoutUrl = response.getCheckout().getUrl();
        }
        log.requestPayload = requestPayload;
        log.responsePayload = responsePayload;
        log.requestedAt = parseTossDateTime(response.getRequestedAt());
        log.approvedAt = parseTossDateTime(response.getApprovedAt());
        log.createdAt = LocalDateTime.now();
        return log;
    }

    private static LocalDateTime parseTossDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }
}
