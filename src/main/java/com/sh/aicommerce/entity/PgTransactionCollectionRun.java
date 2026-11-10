package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** PG 거래 수집 실행. 같은 Job 인스턴스의 재시작은 동일 기록을 사용합니다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pg_transaction_collection_run")
public class PgTransactionCollectionRun {
    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long jobInstanceId;
    @Column(nullable = false)
    private Long jobExecutionId; // 가장 최근 실행 ID
    @Column(nullable = false)
    private LocalDate targetDate; // KST 기준 수집 대상일
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NORMALIZE_UTC)
    @Column(nullable = false)
    private OffsetDateTime startedAt;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NORMALIZE_UTC)
    private OffsetDateTime completedAt;
    private long collectedCount; // 해당 수집 실행에 저장된 거래 수
    private String errorMessage; // 전체 예외 대신 수집 종료 결과 요약

    public PgTransactionCollectionRun(Long jobInstanceId, Long jobExecutionId, LocalDate targetDate) {
        this.jobInstanceId = jobInstanceId;
        this.targetDate = targetDate;
        start(jobExecutionId);
    }

    public void start(Long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
        this.status = Status.RUNNING;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.completedAt = null;
        this.errorMessage = null;
    }

    public void finish(boolean successful, long collectedCount) {
        this.collectedCount = collectedCount;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.status = successful ? Status.COMPLETED : Status.FAILED;
        this.errorMessage = successful ? null : "수집이 완료되지 않았습니다. 해당 JobExecution의 실패 이력을 확인해주세요.";
    }
}
