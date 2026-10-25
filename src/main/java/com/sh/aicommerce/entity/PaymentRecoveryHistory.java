package com.sh.aicommerce.entity;


import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryAction;
import com.sh.aicommerce.enums.paymentRecovery.PaymentRecoveryResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_recovery_history",
        indexes = {
                @Index(
                        name = "idx_recovery_history",
                        columnList = "payment_recovery_id, attempt_number"
                )
        }
)
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecoveryHistory {

    /**
     * 복구 실행 이력 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 복구 작업과의 연관 관계
     *
     * PaymentRecovery 1 : N PaymentRecoveryHistory
     *
     * 하나의 복구 작업에서 여러 번 재시도한
     * 이력을 관리한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_recovery_id",
            nullable = false
    )
    private PaymentRecovery paymentRecovery;

    /**
     * 복구 실행 회차
     *
     * 1 : 첫 번째 복구 실행
     * 2 : 두 번째 복구 실행
     * 3 : 세 번째 복구 실행
     */
    @Column(nullable = false)
    private int attemptNumber;

    /**
     * 실행한 복구 작업 유형
     *
     * PG_STATUS_CHECK : PG 결제 결과 조회
     * DB_APPLY        : 내부 DB 상태 반영
     * MANUAL_RECONCILIATION : 운영자 수동 복구
     *
     * 생성 할 떄는 action에 대한 값이 null 값인 형태로 생성
     */
    @Enumerated(EnumType.STRING)
    private PaymentRecoveryAction action;

    /**
     * 복구 작업 실행 결과
     *
     * SUCCESS      : 작업 성공
     * FAILED       : 작업 실행 실패
     * INCONCLUSIVE : 결제 결과 확정 불가
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRecoveryResult result;

    /**
     * PG 거래 조회에서 확인된 실제 결제 상태
     *
     * Toss에서 반환한 상태를 기록한다.
     * PG 조회 자체가 실패한 경우에는 NOT_FOUND_PAYMENT에 대한 코드 값 입력
     */
    @Column(length = 50)
    private String pgPaymentStatus;

    /**
     * 복구 작업 중 발생한 오류 코드
     *
     * 예: PG_TIMEOUT, DB_CONNECTION_ERROR
     */
    @Column(length = 100)
    private String errorCode;

    /**
     * 복구 작업 중 발생한 오류 메시지
     *
     * 복구 실패 또는 결과 미확정 사유
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * 복구 작업 시작 시각
     */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /**
     * 복구 작업 종료 시각
     *
     * 실행 도중 서버가 종료된 경우 null 가능
     */
    private LocalDateTime completedAt;

    public static PaymentRecoveryHistory createHistory(PaymentRecovery recovery) {
        return PaymentRecoveryHistory.builder()
                .paymentRecovery(recovery)
                .result(PaymentRecoveryResult.CREATE)
                .attemptNumber(recovery.getRetryCount())
                .pgPaymentStatus(null)
                .errorCode(null)
                .errorMessage(null)
                .startedAt(LocalDateTime.now())
                .completedAt(null)
                .build();

    }


    // 재시도 횟수가 초과한 경우 관리자가 확인할 수 있도록 확인
    public void updateHistoryExceedRetryCntNotFoundPayment(String errorCode, String errorMessage) {
        this.result = PaymentRecoveryResult.INCONCLUSIVE;
        this.completedAt = LocalDateTime.now();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;

    }

    public void updateHistoryForNextRetry(String errorCode, String errorMessage) {
        this.action = PaymentRecoveryAction.PG_STATUS_CHECK;
        this.result = PaymentRecoveryResult.INCONCLUSIVE;
        this.pgPaymentStatus = null;
        this.completedAt = LocalDateTime.now();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
