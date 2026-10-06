package com.sh.aicommerce.refund.dto.response;

import com.sh.aicommerce.entity.Refund;
import com.sh.aicommerce.enums.refund.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelFailRefundResponseDto {
    private Long refundId;
    private RefundStatus statusCode;

    private Integer requestedAmount; // 요청한 환불 금액
    private Integer refundedAmount; // PG에서 확인된 취소 금액, 미확인 시 null

    private String failureCode; // PG 원본 오류 코드, 없으면 null
    private String failureReason; // 정제된 실패 사유

    private Integer attemptCount; // 내부에 기록된 작업 시도 횟수
    private LocalDateTime lastAttemptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private OffsetDateTime canceledAt; // PG 취소 시각, 고객 계좌 입금 시각이 아님
    private LocalDateTime completedAt; // 내부 환불 완료 반영 시각

    // Refund 미생성 여부는 호출부에서 확인하고 응답의 refund를 null로 설정한다.
    public OrderCancelFailRefundResponseDto(Refund refund) {
        this.refundId = refund.getId();
        this.statusCode = refund.getRefundStatus();
        this.requestedAmount = refund.getRequestedAmount();
        this.refundedAmount = refund.getRefundedAmount();
        this.failureCode = refund.getFailureCode();
        this.failureReason = refund.getFailureReason();
        this.attemptCount = refund.getAttemptCount();
        this.lastAttemptedAt = refund.getLastAttemptedAt();
        this.createdAt = refund.getCreatedAt();
        this.updatedAt = refund.getUpdatedAt();
        this.canceledAt = refund.getCanceledAt();
        this.completedAt = refund.getCompletedAt();
    }
}
