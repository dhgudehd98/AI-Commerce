package com.sh.aicommerce.admin.batch.dto.response;

import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class AdminCollectionRunResponseDto {
    private final Long id; // 수집 실행 PK
    private final LocalDate targetDate; // 수집 대상일(KST)
    private final PgTransactionCollectionRun.Status status;
    private final Long jobInstanceId;
    private final Long jobExecutionId; // 가장 최근 실행 시도
    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;
    private final long collectedCount; // 종료 시 집계한 저장 건수
    private final String errorMessage;
}
