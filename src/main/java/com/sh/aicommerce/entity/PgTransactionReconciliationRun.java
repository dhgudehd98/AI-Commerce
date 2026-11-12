package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 대사 실행 요약. 거래 불일치가 있어도 검사 자체가 끝났으면 COMPLETED입니다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)

// 기존 테이블 이름 유지. 테이블명 변경은 별도 마이그레이션으로 처리합니다.
@Table(name = "pg_transaction_reconciliation_run")
public class PgTransactionReconciliationRun {
    private static final int ERROR_CODE_MAX_LENGTH = 100;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;


    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long jobInstanceId;

    @Column(nullable = false)
    private Long jobExecutionId; // 가장 최근 실행 시도 ID

    @Column(nullable = false)
    private LocalDate targetDate; // KST 기준 대사 대상일

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_run_id", nullable = false, updatable = false)
    private PgTransactionCollectionRun collectionRun; // 비교 대상으로 고정한 수집 실행

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Builder.Default
    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @TimeZoneStorage(TimeZoneStorageType.NORMALIZE_UTC)
    private OffsetDateTime completedAt;

    private long pgCount; // 선택된 수집 실행의 전체 거래 수
    private long processedCount; // 결과 저장이 완료된 거래 수
    private long matchedCount; // 정상 거래 수
    private long mismatchCount; // 불일치 거래 수. 불일치 사유 수와 구분
    private long excludedCount; // 미지원 상태·판정 유예 거래 수
    private long matchedLedgerCount; // 비교 시 연결된 내부 원장 수. 전체 내부 원장 수는 아님

    // PG 기준 단방향 비교에 참여한 금액. 내부 금액은 매칭된 원장만 포함합니다.
    // 미집계와 실제 합계 0원을 구분하기 위해 nullable로 둡니다.
    private Long pgApprovalAmount;
    private Long internalApprovalAmount;
    private Long pgCancellationAmount;
    private Long internalCancellationAmount;

    @Column(length = ERROR_CODE_MAX_LENGTH)
    private String errorCode; // 배치 자체의 실행 오류

    @Column(length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;
    /** Result 저장과 같은 트랜잭션에서 호출합니다. 불일치 사유가 여러 개여도 거래는 한 건입니다. */
    public void recordResult(PgTransactionReconciliationResult.Status resultStatus, boolean ledgerFound) {
        switch (resultStatus) {
            case MATCHED -> this.matchedCount++;
            case MISMATCHED -> this.mismatchCount++;
            case EXCLUDED -> this.excludedCount++;
        }
        this.processedCount++;
        if (ledgerFound) this.matchedLedgerCount++;
    }

    /** 불일치 발견은 배치 실패가 아닙니다. 검사 실행의 성공·실패만 반영합니다. */
    public void finish(boolean completed, String errorCode, String errorMessage) {
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.status = completed ? Status.COMPLETED : Status.FAILED;
        this.errorCode = completed ? null : limitLength(errorCode, ERROR_CODE_MAX_LENGTH);
        this.errorMessage = completed ? null : limitLength(errorMessage, ERROR_MESSAGE_MAX_LENGTH);
    }

    private String limitLength(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
