package com.sh.aicommerce.admin.batch.dto.response;

import com.sh.aicommerce.entity.PgTransactionReconciliationRun;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class AdminReconciliationRunResponseDto {
    private final Long id; // 대사 실행 PK
    private final Long collectionRunId; // 비교 기준으로 선택한 수집 실행
    private final LocalDate targetDate;
    private final PgTransactionReconciliationRun.Status status;
    private final Long jobInstanceId;
    private final Long jobExecutionId;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;
    private final long pgCount; // 검사 대상 거래 수
    private final long processedCount; // 결과 저장 완료 거래 수
    private final long matchedCount;
    private final long mismatchCount; // 사유 개수가 아닌 불일치 거래 수
    private final long excludedCount;
    private final long matchedLedgerCount; // 비교 원장이 연결된 거래 수
    private final long internalCheckedCount; // 역방향 검사 원장 수
    private final long internalOnlyCount; // PG 거래 누락 원장 수

    public long getTotalMismatchCount() {
        return mismatchCount + internalOnlyCount;
    }

    private final String errorCode;
    private final String errorMessage;
}
