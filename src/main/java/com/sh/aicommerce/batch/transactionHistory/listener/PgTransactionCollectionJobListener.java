package com.sh.aicommerce.batch.transactionHistory.listener;

import com.sh.aicommerce.transaction.service.PgTransactionCollectionRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PgTransactionCollectionJobListener implements JobExecutionListener {
    public static final String COLLECTION_RUN_ID = "collectionRunId";
    private final PgTransactionCollectionRunService service;

    @Override
    public void beforeJob(JobExecution execution) {

        Long runId = service.start(execution.getJobInstance().getInstanceId(), execution.getId(),
                LocalDate.parse(execution.getJobParameters().getString("targetDate")));

        execution.getExecutionContext().putLong(COLLECTION_RUN_ID, runId);
        log.info("[PG 거래 수집 시작] runId={} executionId={}", runId, execution.getId());
    }

    @Override
    public void afterJob(JobExecution execution) {
        if (!execution.getExecutionContext().containsKey(COLLECTION_RUN_ID))
            return;
        long runId = execution.getExecutionContext().getLong(COLLECTION_RUN_ID);

        service.finish(runId, execution.getStatus() == BatchStatus.COMPLETED);
        log.info("[PG 거래 수집 종료] runId={} status={}", runId, execution.getStatus());
    }
}
