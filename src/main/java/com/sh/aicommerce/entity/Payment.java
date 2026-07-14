package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.enums.payment.*;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
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

    @Enumerated(EnumType.STRING)
    private GeneralPayment generalPayment;

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

    private String approvedNumber;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public static Payment createPayment(PaymentRequestDto dto, Integer totalPrice) {
        Payment payment = new Payment();
        payment.paymentMethod = dto.getPaymentMethod();
        payment.generalPayment = dto.getGeneralPayment();
        payment.status = PaymentStatus.READY;
        payment.amount = totalPrice;
        payment.createdAt = LocalDateTime.now();

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

        if (dto.getPaymentMethod() == PaymentMethod.GENERAL) {
            payment.paymentProvider = "TOSS_PAYMENTS";
            payment.paymentMethodLabel = "토스페이먼츠";

            return payment;

        }

        return payment;
    }

    public void setOrder(Orders orders) {
        this.order = orders;
    }
}
