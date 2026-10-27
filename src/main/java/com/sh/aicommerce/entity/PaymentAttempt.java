package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_attempt_idempotency",
                columnNames = "idempotency_key"
        )
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey; // PG 요청 멱등성 키

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(nullable = false)
    private Integer requestAmount; // 승인 요청 금액

    private String orderNumber; // 결제 주문 번호

    private String paymentKey; // 해당 승인 시도의 PG 결제 식별자

    private String failureCode; // 실패 코드

    private String failureMessage; // 실패 사유

    private LocalDateTime createdAt; // 승인 시도 생성 시각

    private LocalDateTime completedAt; // 승인 결과 확정 시각

    public static PaymentAttempt createPaymentAttempt(Payment payment, String orderNumber) {
        return PaymentAttempt.builder()
                .payment(payment)
                .requestAmount(payment.getAmount())
                .orderNumber(orderNumber)
                .idempotencyKey(UUID.randomUUID().toString())
                .status(PaymentAttemptStatus.READY)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void updateAttemptSuccessPay(TossPaymentSuccessResponseDto responseDto) {
        this.requestAmount = responseDto.getTotalAmount();
        this.status = PaymentAttemptStatus.SUCCESS;
        this.paymentKey = responseDto.getPaymentKey();
        this.completedAt = OffsetDateTime
                .parse(responseDto.getApprovedAt())
                .toLocalDateTime();
    }

    public void updateAttemptSuccessPayByRecovery(TossPaymentRequestHistoryResponseDto responseDto) {
        this.requestAmount = responseDto.getTotalAmount();
        this.status = PaymentAttemptStatus.SUCCESS;
        this.paymentKey = responseDto.getPaymentKey();
        this.completedAt = responseDto.getApprovedAt().toLocalDateTime();
    }

    public void updateAttemptStatusToProcessing() {
        this.status = PaymentAttemptStatus.PROCESSING;
    }

    public void updateStatusToRejected() {
        this.status = PaymentAttemptStatus.PAYMENT_REJECTED;
    }

    public void updateStatusToUnknown() {
        this.status = PaymentAttemptStatus.UNKNOWN;
    }

    public void updateStatusToPaymentFailed(String errorCode, String errorMessage) {
        this.status = PaymentAttemptStatus.PAYMENT_FAILED;
        this.failureCode = errorCode;
        this.failureMessage = errorMessage;
    }
}