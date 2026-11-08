package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationResult;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
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

    public static final int MAX_RETRY_COUNT = 3;

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

    @Enumerated(EnumType.STRING)
    private PaymentCompensationResult result;

    private LocalDateTime checkedAt;

    /** 최초 실행을 제외한 추가 실행 횟수입니다. */
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int retryCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_cancellation_id")
    private PaymentCancellation paymentCancellation;

    @Column(length = 100)
    private String lastErrorCode;

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

    public void startProcessing() {
        this.status = PaymentCompensationStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void startRetryProcessing() {
        this.retryCount++;
        startProcessing();
    }

    public void handOffCancellation(PaymentCancellation cancellation) {
        this.paymentCancellation = cancellation;
        this.status = cancellation.getStatus() == PaymentCancellationStatus.SUCCESS
                ? PaymentCompensationStatus.FOLLOW_UP_PENDING
                : PaymentCompensationStatus.CANCELLATION_PENDING;
        this.lastErrorCode = null;
        this.updatedAt = LocalDateTime.now();
    }

    /** 보상 조회에서 PG 취소 성공을 직접 반영한 경우 호출합니다. */
    public void markFollowUpPending() {
        if (this.status != PaymentCompensationStatus.CANCELLATION_PENDING
                || this.paymentCancellation == null
                || this.paymentCancellation.getStatus() != PaymentCancellationStatus.SUCCESS) return;
        this.status = PaymentCompensationStatus.FOLLOW_UP_PENDING;
        this.lastErrorCode = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void defer(String errorCode, boolean manualReview) {

        // 취소 인계·후속 처리 대기는 보상 재시도 PENDING으로 되돌리지 않습니다.
        if (this.status != PaymentCompensationStatus.PROCESSING
                && !(manualReview && this.status == PaymentCompensationStatus.PENDING)) return;
        this.status = manualReview || this.retryCount >= MAX_RETRY_COUNT
                ? PaymentCompensationStatus.MANUAL_REVIEW_REQUIRED
                : PaymentCompensationStatus.PENDING;
        this.lastErrorCode = errorCode;
        this.updatedAt = LocalDateTime.now();
    }

    public void completePaymentNotFound() {
        completeUnapproved(PaymentCompensationResult.PAYMENT_NOT_FOUND, "NOT_FOUND_PAYMENT");
    }

    public void completeUnapproved(PaymentCompensationResult result, String reasonCode) {
        this.result = result;
        this.lastErrorCode = reasonCode;
        this.checkedAt = LocalDateTime.now();
        this.status = PaymentCompensationStatus.COMPLETED;
        this.updatedAt = this.checkedAt;
    }

    public void complete() {
        this.result = PaymentCompensationResult.PG_CANCELED;
        this.status = PaymentCompensationStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }
}
