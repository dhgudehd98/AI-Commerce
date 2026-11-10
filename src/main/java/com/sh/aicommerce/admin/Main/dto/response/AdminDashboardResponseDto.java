package com.sh.aicommerce.admin.Main.dto.response;

import lombok.Getter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
public class AdminDashboardResponseDto {
    private final LocalDate from; // 포함하는 시작일, KST
    private final LocalDate to; // 포함하는 종료일, KST
    private final String timezone = "Asia/Seoul";
    private final OffsetDateTime asOf; // 현재 처리 현황 조회 기준 시각
    private final String currency = "KRW";
    private final long approvalAmount; // 기간 내 승인 원장 금액 합계
    private final long approvalCount; // 기간 내 승인 거래 건수
    private final long cancellationAmount; // 기간 내 취소 원장 금액 합계
    private final long cancellationCount; // 기간 내 취소 거래 건수
    private final long netAmount; // 승인 합계 - 취소 합계. 음수도 가능하며 정산액과 다름
    private final long attentionOrderCount; // 현재 관리자 확인·장기 대기 대상 주문 수, 주문당 중복 제외
    private final long pendingCompensationCount; // 현재 보상 PENDING 작업 수
    private final long unconfirmedCancellationCount; // 현재 PG 취소 PROCESSING·UNKNOWN 작업 수
    private final long followUpPendingCount; // PG 취소 완료 후 후속 처리를 기다리는 보상 작업 수
    private final long retryExhaustedOrderCount; // 현재 자동 재시도 소진 상태인 주문 수

    public AdminDashboardResponseDto(LocalDate from, LocalDate to, OffsetDateTime asOf,
                                     long approvalAmount, long approvalCount,
                                     long cancellationAmount, long cancellationCount,
                                     long attentionOrderCount, long pendingCompensationCount,
                                     long unconfirmedCancellationCount, long followUpPendingCount,
                                     long retryExhaustedOrderCount) {
        this.from = from;
        this.to = to;
        this.asOf = asOf;
        this.approvalAmount = approvalAmount;
        this.approvalCount = approvalCount;
        this.cancellationAmount = cancellationAmount;
        this.cancellationCount = cancellationCount;
        this.netAmount = approvalAmount - cancellationAmount;
        this.attentionOrderCount = attentionOrderCount;
        this.pendingCompensationCount = pendingCompensationCount;
        this.unconfirmedCancellationCount = unconfirmedCancellationCount;
        this.followUpPendingCount = followUpPendingCount;
        this.retryExhaustedOrderCount = retryExhaustedOrderCount;
    }
}
