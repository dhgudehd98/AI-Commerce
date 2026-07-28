package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.enums.payment.*;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Orders order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    private String provider;

    private String cardCode;
    @Enumerated(EnumType.STRING)
    private CardCompany cardCompany;

    private Integer installmentMonths; // 0 || 1 -> 일시불 , 그 외 할부

    private Long savedCardId;
    private Long savedAccountId;

    @Column(nullable = false)
    private String paymentProvider;

    @Column(nullable = false)
    private String paymentMethodLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private Integer amount;

    @Column(unique = true)
    private String paymentKey; // 결제사(PG)가 발급하는 결제 건의 식별자

    private String approvedNumber; // 승인번호

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;

    public static Payment createPayment(PaymentRequestDto dto, Integer totalPrice) {
        Payment payment = new Payment();
        payment.paymentMethod = dto.getPaymentMethod();
        payment.status = PaymentStatus.READY;
        payment.amount = totalPrice;
        payment.createdAt = LocalDateTime.now();
        payment.updatedAt = LocalDateTime.now();

        if (dto.getPaymentMethod() == PaymentMethod.SAVED_CARD) {
            payment.savedCardId = dto.getSavedCardId();
            payment.paymentProvider = "PG_BILLING";
            payment.paymentMethodLabel = "등록 카드 간편결제";
            return payment;
        }

        if (dto.getPaymentMethod() == PaymentMethod.SAVED_ACCOUNT) {
            payment.savedAccountId = dto.getSavedAccountId();
            payment.paymentProvider = "PG_BILLING";
            payment.paymentMethodLabel = "등록 계좌 간편결제";
            return payment;
        }

        if (dto.getPaymentMethod() == PaymentMethod.TOSS) {
            payment.paymentProvider = "TOSS_PAYMENTS";
            payment.paymentMethodLabel = "토스페이먼츠 일반결제";

            return payment;

        }

        return payment;
    }

    public void setStatusConfirmingInWidget(String paymentKey) {
        if (this.status != PaymentStatus.READY) {
            throw new PaymentException("결제 승인 가능한 상태가 아닙니다.");
        }
        this.paymentKey = paymentKey;
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentStatus.CONFIRMING;
    }

    public void setStatusConfirmingInBillingCard() {
        if (this.status != PaymentStatus.READY) {
            throw new PaymentException("결제 승인 가능한 상태가 아닙니다.");
        }
        this.status = PaymentStatus.CONFIRMING;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCardPaymentByTossWidget(TossPaymentRequestDto request, TossPaymentSuccessResponseDto response) {
        if (response == null) {
            throw new PaymentException("토스 결제 승인 응답이 존재하지 않습니다.");
        }

        if (response.getCard() == null) {
            throw new PaymentException("카드 결제 승인 정보가 존재하지 않습니다.");
        }

        if (!"DONE".equals(response.getStatus())) {
            throw new PaymentException("토스 결제 승인이 완료되지 않았 습니다.");
        }

        this.paidAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.provider = response.getMethod();
        this.approvedNumber = response.getCard().getApproveNo();
        this.paymentKey = request.getPaymentKey();
        this.cardCode = response.getCard().getIssuerCode();
        this.cardCompany =
                CardCompany.fromCode(response.getCard().getIssuerCode());
        this.status = PaymentStatus.PAID;
        this.installmentMonths =
                response.getCard().getInstallmentPlanMonths();
    }

    public void updateEasyPaymentByTossWidget(TossPaymentRequestDto request, TossPaymentSuccessResponseDto response) {
        this.paidAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.paymentKey = request.getPaymentKey();
        this.provider = response.getEasyPay().getProvider();
        this.status = PaymentStatus.PAID;
    }


    public void updateCardByBillingCard(TossPaymentSuccessResponseDto response) {
        if (response == null) {
            throw new PaymentException("토스 결제 승인 응답이 존재하지 않습니다.");
        }
        if (!"DONE".equals(response.getStatus())) {
            throw new PaymentException("토스 자동결제 승인이 완료되지 않았 습니다.");
        }

        this.paidAt = LocalDateTime.now();
        this.paymentKey = response.getPaymentKey();
        this.provider = response.getMethod(); // "카드"
        this.approvedNumber = response.getCard().getApproveNo();
        this.cardCode = response.getCard().getIssuerCode();
        this.cardCompany = CardCompany.fromCode(response.getCard().getIssuerCode());
        this.installmentMonths = response.getCard().getInstallmentPlanMonths();
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentStatus.PAID;
    }

    public void setOrder(Orders orders) {
        this.order = orders;
    }

    public void updateFailPaymentTossWidget() {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }
}
