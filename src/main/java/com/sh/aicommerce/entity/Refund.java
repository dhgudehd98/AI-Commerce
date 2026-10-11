package com.sh.aicommerce.entity;


import com.sh.aicommerce.enums.refund.RefundStatus;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Refund {

    @Id
    @Column(name = "refund_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_cancellation_id",
            nullable = false,
            unique = true
    )
    private OrderCancellation orderCancellation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private RefundStatus refundStatus;

    private Integer requestedAmount; // PG에 요청할 환불 금액

    private Integer refundedAmount; // PG에서 확인한 실제 취소 금액

    private String reason; // 취소 사유
    private String idempotencyKey; // 같은 환불의 재요청에 사용할 고유 키

    private String paymentKey; // 원결제 식별자
    private String transactionKey; // 취소 성공 거래 식별자
    private String failureCode; // 최근 실패 , 확인 필요 오류 코드
    private String failureReason; // 최근 실패, 확인 필요 사유
    private Integer attemptCount; // PG 취소 요청 시도 횟수
    private LocalDateTime lastAttemptedAt; // 마지막 PG 취소 요청 시도 시간
    private LocalDateTime createdAt; // 환불 작업 생성 시간
    private LocalDateTime updatedAt; // 환불 작업 변경 시간
    private OffsetDateTime canceledAt; // PG가 반환한 실제 취소 시간
    private LocalDateTime completedAt; // 환불 성공을 확인, 반영한 시간

    public static Refund createRefund(OrderCancellation cancellation, Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        Refund refund = new Refund();
        refund.orderCancellation = cancellation;
        refund.payment = payment;
        refund.paymentKey = payment.getPaymentKey();
        refund.refundStatus = RefundStatus.PROCESSING;
        refund.reason = cancellation.getCancelReason();
        refund.requestedAmount = payment.getAmount();
        refund.attemptCount = 1;

        refund.lastAttemptedAt = now;
        refund.createdAt = now;
        refund.updatedAt = now;

        return refund;
    }

    public void updateStatusToCompleted(TossPaymentCancelResponseDto responseDto) {
        LocalDateTime now = LocalDateTime.now();
        this.transactionKey = responseDto.getCancels().get(0).getTransactionKey();
        this.refundedAmount = responseDto.getCancels().get(0).getCancelAmount();
        this.refundStatus = RefundStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
        this.canceledAt = responseDto.getCancels().get(0).getCanceledAt();
    }

    public void updateStatusToFailed(String errorCode, String failReason) {
        this.failureCode = errorCode;
        this.failureReason = failReason;
        this.updatedAt = LocalDateTime.now();
        this.refundStatus = RefundStatus.FAILED;
    }

    public void updateStatusToFailedCheck(String failReason) {
        this.failureReason = failReason;
        this.updatedAt = LocalDateTime.now();
        this.refundStatus = RefundStatus.FAILED_CHECK;
    }

    public void retryUpdateStatusToComplete(TossPaymentRequestHistoryResponseDto response) {
        this.failureCode = null;
        this.failureReason = null;
        LocalDateTime now = LocalDateTime.now();

        this.transactionKey = response.getCancels().get(0).getTransactionKey();
        this.refundedAmount = response.getCancels().get(0).getCancelAmount();
        this.refundStatus = RefundStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
        this.canceledAt = response.getCancels().get(0).getCanceledAt();
    }
}