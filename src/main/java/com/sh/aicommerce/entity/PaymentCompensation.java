package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_compensation", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_compensation_attempt", columnNames = "payment_attempt_id"))
public class PaymentCompensation {

    private static final String APPROVAL_RESULT_UNKNOWN = "APPROVAL_RESULT_UNKNOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id", nullable = false, updatable = false)
    private PaymentAttempt paymentAttempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentCompensationStatus status;

    @Column(nullable = false, updatable = false)
    private Integer requestAmount;

    @Column(nullable = false, updatable = false)
    private String orderNumber;

    @Column(nullable = false, updatable = false)
    private String reasonCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PaymentCompensation create(PaymentAttempt attempt) {
        if (attempt == null || attempt.getRequestAmount() == null || attempt.getRequestAmount() <= 0
                || attempt.getOrderNumber() == null || attempt.getOrderNumber().isBlank()) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 유효한 승인 시도와 요청 금액·주문번호가 필요합니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentCompensation compensation = new PaymentCompensation();
        compensation.paymentAttempt = attempt;
        compensation.status = PaymentCompensationStatus.PENDING;
        compensation.requestAmount = attempt.getRequestAmount();
        compensation.orderNumber = attempt.getOrderNumber();
        // 외부 예외 전문·빌링키·개인정보는 보상 작업에 복사하지 않습니다.
        compensation.reasonCode = APPROVAL_RESULT_UNKNOWN;
        compensation.createdAt = now;
        compensation.updatedAt = now;
        return compensation;
    }
}
