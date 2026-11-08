package com.sh.aicommerce.admin.PaymentCompensation.dto.response;

import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationResult;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class AdminPaymentCompensationListResponseDto {
    private final Long compensationId; // 보상 작업 ID
    private final Long paymentId; // 원본 결제 ID
    private final Long attemptId; // 보상을 유발한 승인 시도 ID
    private final Long paymentCancellationId; // 연결된 PG 취소 ID: 취소 인계 전에는 null
    private final String orderNumber; // 보상 작업 생성 당시 주문번호
    private final Long memberId; // 회원 ID
    private final String memberName; // 주문 회원 이름
    private final Integer requestAmount; // 보상 대상 결제 요청 금액
    private final String paymentStatus; // 현재 결제 상태
    private final String paymentStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String compensationStatus; // 보상 처리 상태
    private final String compensationStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String result; // 보상 종료 결과: 미완료면 null
    private final String resultLabel; // 화면 표시명, 값이 없으면 null
    private final int retryCount; // 최초 실행을 제외한 추가 재시도 횟수
    private final String reasonCode; // 보상 생성 사유 코드
    private final String lastErrorCode; // 마지막 오류 또는 내부 종료 사유 코드
    private final String cancellationStatus; // 연결된 PG 취소 상태: 인계 전에는 null
    private final String cancellationStatusLabel; // 화면 표시명, 값이 없으면 null
    private final LocalDateTime createdAt; // 보상 작업 생성 시각
    private final LocalDateTime updatedAt; // 보상 작업 마지막 갱신 시각
    private final LocalDateTime checkedAt; // 미조회·미승인 종료 확인 시각: 미기록이면 null
    private final int maxRetryCount = com.sh.aicommerce.entity.PaymentCompensation.MAX_RETRY_COUNT; // 추가 재시도 한도

    public AdminPaymentCompensationListResponseDto(
            Long compensationId,
            Long paymentId,
            Long attemptId,
            Long paymentCancellationId,
            String orderNumber,
            Long memberId,
            String memberName,
            Integer requestAmount,
            PaymentStatus paymentStatus,
            PaymentCompensationStatus compensationStatus,
            PaymentCompensationResult result,
            int retryCount,
            String reasonCode,
            String lastErrorCode,
            PaymentCancellationStatus cancellationStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime checkedAt) {
        this.compensationId = compensationId;
        this.paymentId = paymentId;
        this.attemptId = attemptId;
        this.paymentCancellationId = paymentCancellationId;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.memberName = memberName;
        this.requestAmount = requestAmount;
        this.paymentStatus = paymentStatus == null ? null : paymentStatus.name();
        this.paymentStatusLabel = paymentStatus == null ? null : paymentStatus.getStatus();
        this.compensationStatus = compensationStatus == null ? null : compensationStatus.name();
        this.compensationStatusLabel = compensationStatus == null ? null : switch (compensationStatus) {
            case PENDING -> "보상 대기";
            case PROCESSING -> "보상 처리 중";
            case CANCELLATION_PENDING -> "PG 취소 결과 대기";
            case FOLLOW_UP_PENDING -> "PG 취소 완료 후속 처리 대기";
            case COMPLETED -> "보상 완료";
            case MANUAL_REVIEW_REQUIRED -> "관리자 확인 필요";
        };
        this.result = result == null ? null : result.name();
        this.resultLabel = result == null ? null : switch (result) {
            case PAYMENT_NOT_FOUND -> "결제 내역 없음으로 종료";
            case APPROVAL_ABORTED -> "승인 실패로 종료";
            case PAYMENT_EXPIRED -> "거래 만료로 종료";
            case PG_CANCELED -> "PG 취소 및 후속 처리 완료";
        };
        this.retryCount = retryCount;
        this.reasonCode = reasonCode;
        this.lastErrorCode = lastErrorCode;
        this.cancellationStatus = cancellationStatus == null ? null : cancellationStatus.name();
        this.cancellationStatusLabel = cancellationStatus == null ? null : cancellationStatus.getDescription();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.checkedAt = checkedAt;
    }
}
