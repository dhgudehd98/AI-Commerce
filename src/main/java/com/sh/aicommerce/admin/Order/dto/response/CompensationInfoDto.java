package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CompensationInfoDto {
    private Long compensationId; // 보상 ID
    private Long attemptId; // 관련 승인 시도 ID
    private Long paymentCancellationId; // 연결된 PG 취소 ID
    private String status; // 현재 보상 상태
    private String result; // 종료 결과, 미완료면 null
    private Integer requestAmount; // 보상 대상 금액
    private String reasonCode; // 보상 생성 사유
    private String lastErrorCode; // 마지막 오류 또는 종료 사유 코드
    private int retryCount; // 최초 실행을 제외한 추가 재시도 횟수
    private int maxRetryCount; // 추가 재시도 한도
    private LocalDateTime createdAt; // 타임라인의 보상 시작 시간: 보상 작업 생성 시각
    private LocalDateTime updatedAt; // 마지막 갱신 시각, 개별 재시도 시각은 아님
    private LocalDateTime checkedAt; // 미조회·미승인 종료 확인 시각, 없으면 null

    public CompensationInfoDto(PaymentCompensation compensation) {
        this.compensationId = compensation.getId();
        this.attemptId = compensation.getPaymentAttempt().getId();
        this.paymentCancellationId = compensation.getPaymentCancellation() == null ? null : compensation.getPaymentCancellation().getId();
        this.status = compensation.getStatus().name();
        this.result = compensation.getResult() == null ? null : compensation.getResult().name();
        this.requestAmount = compensation.getRequestAmount();
        this.reasonCode = compensation.getReasonCode();
        this.lastErrorCode = compensation.getLastErrorCode();
        this.retryCount = compensation.getRetryCount();
        this.maxRetryCount = PaymentCompensation.MAX_RETRY_COUNT;
        this.createdAt = compensation.getCreatedAt();
        this.updatedAt = compensation.getUpdatedAt();
        this.checkedAt = compensation.getCheckedAt();
    }
}
