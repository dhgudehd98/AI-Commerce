package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
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

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime updatedAt;

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
}