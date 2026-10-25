package com.sh.aicommerce.entity;

import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.common.exception.payment.PaymentException;
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

    // 취소 로그 전용 필드. amount는 최초 결제 금액으로 유지한다.
    private Long cancellationId;
    private String cancelTransactionKey;
    private String cancelStatus;
    private String cancelReason;
    private Integer cancelAmount;
    private OffsetDateTime canceledAt;
    private Integer cancelTaxFreeAmount;
    private Integer cancelTaxExemptionAmount;
    private Integer refundableAmount;
    private Integer transferDiscountAmount;
    private Integer easyPayDiscountAmount;
    private String cancelReceiptKey;
    private String cancelRequestId;
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

    public static TossPaymentLog confirmSuccess(
            Payment payment,
            TossPaymentRequestHistoryResponseDto response,
            Integer httpStatus,
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
        log.responsePayload = responsePayload;
        log.requestedAt = response.getRequestedAt().toLocalDateTime();
        log.approvedAt = response.getApprovedAt().toLocalDateTime();
        log.createdAt = LocalDateTime.now();
        return log;
    }

    /**
     * 서비스에서 전액 취소 응답 검증 후 호출한다.
     * 승인 로그 필드는 유지하고, 취소 거래는 별도 필드에 기록한다.
     * 원문 응답·HTTP 상태·요청 시각은 이 DTO만으로 확정할 수 없어 임의로 채우지 않는다.
     */
    public static TossPaymentLog createStatusToCancelSuccess(Payment payment,
            TossPaymentCancelResponseDto response, Long cancellationId) {
        if (response == null || response.getCancels() == null
                || response.getCancels().size() != 1 || response.getCancels().get(0) == null) {
            throw new PaymentException("취소 로그에는 검증된 취소 거래 한 건이 필요합니다.");
        }
        CancelResponseDto cancel = response.getCancels().get(0);
        TossPaymentLog log = new TossPaymentLog();
        log.payment = payment;
        log.cancellationId = cancellationId;
        log.requestType = "CANCEL";
        log.mid = response.getMId();
        log.paymentKey = response.getPaymentKey();
        log.orderNumber = response.getOrderId();
        log.orderName = response.getOrderName();
        log.lastTransactionKey = response.getLastTransactionKey();
        log.tossStatus = response.getStatus();
        log.method = response.getMethod();
        log.currency = response.getCurrency();
        log.amount = response.getTotalAmount();
        log.balanceAmount = response.getBalanceAmount();
        log.suppliedAmount = response.getSuppliedAmount();
        log.vat = response.getVat();
        log.taxFreeAmount = response.getTaxFreeAmount();
        if (response.getEasyPay() != null) {
            log.easyPayProvider = response.getEasyPay().isTextual()
                    ? response.getEasyPay().asText()
                    : response.getEasyPay().path("provider").asText(null);
        }
        log.cancelTransactionKey = cancel.getTransactionKey();
        log.cancelStatus = cancel.getCancelStatus();
        log.cancelReason = cancel.getCancelReason();
        log.cancelAmount = cancel.getCancelAmount();
        log.canceledAt = cancel.getCanceledAt();
        log.cancelTaxFreeAmount = cancel.getTaxFreeAmount();
        log.cancelTaxExemptionAmount = cancel.getTaxExemptionAmount();
        log.refundableAmount = cancel.getRefundableAmount();
        log.transferDiscountAmount = cancel.getTransferDiscountAmount();
        log.easyPayDiscountAmount = cancel.getEasyPayDiscountAmount();
        log.cancelReceiptKey = cancel.getReceiptKey();
        log.cancelRequestId = cancel.getCancelRequestId();
        log.createdAt = LocalDateTime.now();
        return log;
    }

    private static LocalDateTime parseTossDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    public static TossPaymentLog createStatusToCancelSuccessInRetry(Long cancellationId, Payment payment, TossPaymentRequestHistoryResponseDto historyResponse) {
        TossPaymentLog log = new TossPaymentLog();
        CancelResponseDto cancel = historyResponse.getCancels().get(0);

        log.payment = payment;
        log.cancellationId = cancellationId;
        log.requestType = "CANCEL";
        log.mid = historyResponse.getMId();
        log.paymentKey = historyResponse.getPaymentKey();
        log.orderNumber = historyResponse.getOrderId();
        log.orderName = historyResponse.getOrderName();
        log.lastTransactionKey = historyResponse.getLastTransactionKey();
        log.tossStatus = historyResponse.getStatus();
        log.method = historyResponse.getMethod();
        log.currency = historyResponse.getCurrency();
        log.amount = historyResponse.getTotalAmount();
        log.balanceAmount = historyResponse.getBalanceAmount();
        log.suppliedAmount = historyResponse.getSuppliedAmount();
        log.vat = historyResponse.getVat();
        log.taxFreeAmount = historyResponse.getTaxFreeAmount();
        if (historyResponse.getEasyPay() != null) {
            log.easyPayProvider = historyResponse.getEasyPay().getProvider();
        }

        log.cancelTransactionKey = cancel.getTransactionKey();
        log.cancelStatus = cancel.getCancelStatus();
        log.cancelReason = cancel.getCancelReason();
        log.cancelAmount = cancel.getCancelAmount();
        log.canceledAt = cancel.getCanceledAt();
        log.cancelTaxFreeAmount = cancel.getTaxFreeAmount();
        log.cancelTaxExemptionAmount = cancel.getTaxExemptionAmount();
        log.refundableAmount = cancel.getRefundableAmount();
        log.transferDiscountAmount = cancel.getTransferDiscountAmount();
        log.easyPayDiscountAmount = cancel.getEasyPayDiscountAmount();
        log.cancelReceiptKey = cancel.getReceiptKey();
        log.cancelRequestId = cancel.getCancelRequestId();
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
