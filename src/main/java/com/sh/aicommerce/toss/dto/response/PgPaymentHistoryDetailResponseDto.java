package com.sh.aicommerce.toss.dto.response;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/** 관리자 화면용 PG 조회 결과. 결제키와 외부 응답 전문은 반환하지 않는다. */
@Getter
public class PgPaymentHistoryDetailResponseDto {
    private final OffsetDateTime queriedAt;
    private final PaymentInfo payment;
    private final PaymentMethodInfo paymentMethod;
    private final List<CancelInfo> cancels;

    public static PgPaymentHistoryDetailResponseDto from(TossPaymentRequestHistoryResponseDto response) {
        return new PgPaymentHistoryDetailResponseDto(response);
    }

    public PgPaymentHistoryDetailResponseDto(TossPaymentRequestHistoryResponseDto response) {
        if (response == null) {
            throw new PaymentException("[PG 결제 조회 결과 변환] 조회 응답이 없습니다.");
        }
        this.queriedAt = OffsetDateTime.now();
        this.payment = new PaymentInfo(
                response.getOrderId(), response.getOrderName(), response.getType(),
                response.getMethod(), response.getStatus(), response.getCurrency(),
                response.getTotalAmount(), response.getBalanceAmount(),
                response.getRequestedAt(), response.getApprovedAt(),
                response.getIsPartialCancelable(),
                response.getReceipt() == null ? null : response.getReceipt().getUrl(),
                response.getFailure() == null ? null : new FailureInfo(
                        response.getFailure().getCode(), response.getFailure().getMessage()));

        CardResponse card = response.getCard();
        EasyPayResponse easyPay = response.getEasyPay();
        var transfer = response.getTransfer();
        var account = response.getVirtualAccount();
        var phone = response.getMobilePhone();
        var gift = response.getGiftCertificate();
        // 간편결제와 카드 정보가 동시에 존재할 수 있으므로 각각 변환한다.
        this.paymentMethod = new PaymentMethodInfo(
                card == null ? null : new CardInfo(card.getIssuerCode(), card.getAcquirerCode(),
                        maskNumber(card.getNumber()), card.getInstallmentPlanMonths(),
                        card.getIsInterestFree(), card.getApproveNo(), card.getCardType(),
                        card.getOwnerType(), card.getAcquireStatus(), card.getAmount()),
                easyPay == null ? null : new EasyPayInfo(easyPay.getProvider(),
                        easyPay.getAmount(), easyPay.getDiscountAmount()),
                transfer == null ? null : new TransferInfo(transfer.getBankCode(), transfer.getSettlementStatus()),
                account == null ? null : new VirtualAccountInfo(account.getAccountType(),
                        maskNumber(account.getAccountNumber()), account.getBankCode(), account.getDueDate(),
                        account.getRefundStatus(), account.getExpired(), account.getSettlementStatus()),
                phone == null ? null : new MobilePhoneInfo(maskNumber(phone.getCustomerMobilePhone()),
                        phone.getSettlementStatus(), phone.getReceiptUrl()),
                gift == null ? null : new GiftCertificateInfo(gift.getApproveNo(), gift.getSettlementStatus()));

        // 이력이 없으면 빈 배열. 부분 취소를 포함한 모든 거래를 유지한다.
        this.cancels = response.getCancels() == null ? List.of() : response.getCancels().stream()
                .map(cancel -> {
                    if (cancel == null) {
                        throw new PaymentException("[PG 결제 조회 결과 변환] 취소 이력에 빈 거래가 포함되어 있습니다.");
                    }
                    return new CancelInfo(cancel.getTransactionKey(), cancel.getCancelStatus(),
                            cancel.getCancelAmount(), cancel.getCancelReason(), cancel.getCanceledAt(),
                            cancel.getRefundableAmount(), cancel.getTaxFreeAmount(),
                            cancel.getTaxExemptionAmount(), cancel.getCardDiscountAmount(),
                            cancel.getTransferDiscountAmount(), cancel.getEasyPayDiscountAmount());
                }).toList();
    }

    // 이미 마스킹된 PG 번호는 유지하고, 원문 번호는 마지막 네 자리만 표시한다.
    private static String maskNumber(String number) {
        if (number == null || number.isBlank() || number.contains("*")) return number;
        return "*".repeat(Math.max(0, number.length() - 4))
                + (number.length() > 4 ? number.substring(number.length() - 4) : "*".repeat(number.length()));
    }

    public record PaymentInfo(
            String orderNumber, String orderName, String type, String method, String status,
            String currency, Integer totalAmount, Integer balanceAmount,
            OffsetDateTime requestedAt, OffsetDateTime approvedAt,
            Boolean partialCancelable, String receiptUrl, FailureInfo failure
    ) {}

    public record FailureInfo(String code, String message) {}

    public record PaymentMethodInfo(
            CardInfo card, EasyPayInfo easyPay, TransferInfo transfer,
            VirtualAccountInfo virtualAccount, MobilePhoneInfo mobilePhone,
            GiftCertificateInfo giftCertificate
    ) {}

    public record CardInfo(
            String issuerCode, String acquirerCode, String maskedNumber,
            Integer installmentPlanMonths, Boolean interestFree, String approveNo,
            String cardType, String ownerType, String acquireStatus, Integer amount
    ) {}

    public record EasyPayInfo(String provider, Integer amount, Integer discountAmount) {}
    public record TransferInfo(String bankCode, String settlementStatus) {}
    public record VirtualAccountInfo(
            String accountType, String maskedAccountNumber, String bankCode, String dueDate,
            String refundStatus, Boolean expired, String settlementStatus
    ) {}
    public record MobilePhoneInfo(String maskedPhoneNumber, String settlementStatus, String receiptUrl) {}
    public record GiftCertificateInfo(String approveNo, String settlementStatus) {}

    public record CancelInfo(
            String transactionKey, String status, Integer amount, String reason,
            OffsetDateTime canceledAt, Integer refundableAmount, Integer taxFreeAmount,
            Integer taxExemptionAmount, Integer cardDiscountAmount,
            Integer transferDiscountAmount, Integer easyPayDiscountAmount
    ) {}
}
