package com.sh.aicommerce.entity;


import co.elastic.clients.elasticsearch.indices.recovery.RecoveryStatus;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_recovery",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_recovery_attempt",
                        columnNames = "payment_attempt_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_recovery_schedule",
                        columnList = "status"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecovery {

    /**
     * 결제 복구 작업 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 복구 대상 결제 승인 시도
     *
     * PaymentAttempt 하나당 PaymentRecovery 하나를 생성한다.
     * 동일한 결제 승인 시도를 여러 번 복구하더라도
     * PaymentRecovery는 새로 생성하지 않는다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_attempt_id",
            nullable = false
    )
    private PaymentAttempt paymentAttempt;

    /**
     * 복구 작업의 현재 진행 상태
     *
     * WAITING       : 최초 복구 대기
     * PROCESSING    : 복구 진행 중
     * RETRY_WAIT    : 다음 복구 시도 대기
     * COMPLETED     : 복구 완료
     * MANUAL_REVIEW : 운영자 확인 필요
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRecoveryStatus status;

    /**
     * 현재까지 실행한 자동 복구 시도 횟수
     *
     * 최초 값: 0
     * 1차 실행 후: 1
     * 2차 실행 후: 2
     * 3차 실행 후: 3
     *
     * retryCount >= maxRetryCount인 경우
     * 자동 복구를 중단한다.
     */
    @Column(nullable = false)
    private int retryCount = 0;

    /**
     * 허용되는 최대 자동 복구 시도 횟수
     *
     * 기본값: 3회
     */
    @Column(nullable = false)
    @Builder.Default
    private int maxRetryCount = 3;

    /**
     * 마지막 복구 시도 시각
     *
     * 스케줄러가 실제 복구 작업을 시작한 시각
     */
    private LocalDateTime lastRetryAt;

    /**
     * 마지막 복구 실패 오류 코드
     *
     * 예: PG_TIMEOUT, DB_CONNECTION_ERROR
     */
    @Column(length = 100)
    private String lastErrorCode;

    /**
     * 마지막 복구 실패 사유
     *
     * 상세 오류 메시지 또는 복구 실패 원인
     */
    @Column(length = 1000)
    private String lastErrorMessage;

    /**
     * 복구 작업 완료 시각
     *
     * PG 결과 확인 및 내부 DB 상태 반영이
     * 정상적으로 완료된 시각
     */
    private LocalDateTime completedAt;

    /**
     * 복구 작업 생성 시각
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 복구 작업 수정 시각
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PaymentRecovery createRecovery(PaymentAttempt attempt) {
        return PaymentRecovery.builder()
                .paymentAttempt(attempt)
                .status(PaymentRecoveryStatus.RETRY_WAIT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastRetryAt(LocalDateTime.now())
                .build();
    }

    public void updateRecovery() {
        LocalDateTime now = LocalDateTime.now();
        this.status = PaymentRecoveryStatus.COMPLETED;

        this.completedAt = now;
        this.updatedAt = now;
        this.lastRetryAt = now;
    }

    public void startRecovery() {
        this.retryCount += 1;
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentRecoveryStatus.PROCESSING;
    }

    public void updateRecoveryToManualCheck(String errorCode, String lastErrorMessage) {
        this.status = PaymentRecoveryStatus.MANUAL_REVIEW;
        this.lastRetryAt = LocalDateTime.now();
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = lastErrorMessage;

        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRecoveryForNextRetry(String errorCode, String errorMessage) {
        this.status = PaymentRecoveryStatus.RETRY_WAIT; // 다음 재시도 대기
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;

        this.updatedAt = LocalDateTime.now();
    }
}
