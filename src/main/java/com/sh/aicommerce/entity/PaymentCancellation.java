package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation {

    public static final int FAILURE_MESSAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_cancellation_id", nullable = false, unique = true)
    private OrderCancellation orderCancellation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentCancellationStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Integer cancelAmount;

    @Column(nullable = false)
    private String cancelReason;

    private String failCode;
    @Column(length = FAILURE_MESSAGE_MAX_LENGTH)
    private String failMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime updatedAt;

    private LocalDateTime canceledAt;

    public static PaymentCancellation createPaymentCancellation(Payment p, OrderCancellation cancellation) {
        return PaymentCancellation.builder()
                .orderCancellation(cancellation)
                .payment(p)
                .status(PaymentCancellationStatus.READY)
                .idempotencyKey(UUID.randomUUID().toString())
                .cancelAmount(p.getAmount())
                .cancelReason(cancellation.getCancelReason())
                .build();
    }

    public void startPaymentCancellation() {
        this.status = PaymentCancellationStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancelSuccess(CancelResponseDto cancelResponseDto) {
        this.cancelAmount = cancelResponseDto.getCancelAmount();
        this.status = PaymentCancellationStatus.SUCCESS;

        this.canceledAt = cancelResponseDto.getCanceledAt().toLocalDateTime();
        this.updatedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
    }

    // Toss API에서 취소 거절 코드가 있는 경우
    public void updateStatusToRejected(String code, String message) {
        this.status = PaymentCancellationStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();

        this.failCode = code;
        this.failMessage = message;
    }

    public void updateStatusToUnknown(String message) {
        this.failCode = null;
        this.failMessage = message;
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentCancellationStatus.UNKNOWN;
    }
}