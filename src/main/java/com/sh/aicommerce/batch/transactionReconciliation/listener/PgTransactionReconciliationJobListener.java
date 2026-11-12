package com.sh.aicommerce.batch.transactionReconciliation.listener;

import com.sh.aicommerce.transactionReconciliation.dto.response.ReconciliationStartResult;
import com.sh.aicommerce.transactionReconciliation.service.PgTransactionReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class PgTransactionReconciliationJobListener implements JobExecutionListener {

    public static final String RECONCILIATION_RUN_ID = "reconciliationRunId";
    public static final String COLLECTION_RUN_ID = "collectionRunId";
    private final PgTransactionReconciliationService reconciliationService;

    @Override
    public void beforeJob(JobExecution execution) {
        // 거래 대사 배치 실행 전, 거래 대사 배치 저장
        ReconciliationStartResult result = reconciliationService.start(
                execution.getJobInstance().getInstanceId(),
                execution.getId(),
                LocalDate.parse(execution.getJobParameters().getString("targetDate"))
        );

        // 거래 대사 배치 ID 저장
        execution.getExecutionContext().putLong(RECONCILIATION_RUN_ID, result.reconciliationRunId());
        // 수집 배치 ID 저장
        execution.getExecutionContext().putLong(COLLECTION_RUN_ID, result.collectionRunId());
    }

    @Override
    public void afterJob(JobExecution execution) {
        // 시작 단계에서 수집 검증이 실패한 경우에는 생성된 대사 Run이 없습니다.
        if (!execution.getExecutionContext().containsKey(RECONCILIATION_RUN_ID)) {
            return;
        }

        boolean completed = execution.getStatus() == BatchStatus.COMPLETED;
        String errorMessage = null;
        if (!completed) {
            errorMessage = "배치 종료 상태: " + execution.getStatus();
            if (!execution.getAllFailureExceptions().isEmpty()) {
                Throwable failure = execution.getAllFailureExceptions().get(0);
                errorMessage += " | " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
            }
        }
        reconciliationService.finish(
                execution.getExecutionContext().getLong(RECONCILIATION_RUN_ID),
                completed,
                completed ? null : execution.getExitStatus().getExitCode(),
                errorMessage
        );
    }
}
