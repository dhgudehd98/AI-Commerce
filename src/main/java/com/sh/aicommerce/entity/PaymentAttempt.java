package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_attempt_idempotency",
                columnNames = "idempotency_key"
        )
)
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

    private String paymentKey; // 해당 승인 시도의 PG 결제 식별자

    private String failureCode; // 실패 코드

    private String failureMessage; // 실패 사유

    private LocalDateTime requestedAt; // 승인 시도 생성 시각

    private LocalDateTime completedAt; // 승인 결과 확정 시각
}