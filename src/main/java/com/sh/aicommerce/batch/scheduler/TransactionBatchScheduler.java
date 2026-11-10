package com.sh.aicommerce.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionBatchScheduler {

    private static final ZoneId BATCH_ZONE = ZoneId.of("Asia/Seoul");

    private final JobLauncher jobLauncher;
    private final Job pgTransactionCollectionJob;

    // 매일 KST 00:10에 전날 거래 수집
//    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void runPgTransactionCollection() {

        LocalDate targetDate = LocalDate.now(BATCH_ZONE).minusDays(1);

        JobParameters parameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .toJobParameters();

        try {
            JobExecution execution =
                    jobLauncher.run(pgTransactionCollectionJob, parameters);

            log.info("[PG 거래 수집 배치] targetDate={} executionId={} status={}",
                    targetDate, execution.getId(), execution.getStatus());
        } catch (JobExecutionException e) {
            log.error("[PG 거래 수집 배치 실행 실패] targetDate={}", targetDate, e);
        }
    }
}
