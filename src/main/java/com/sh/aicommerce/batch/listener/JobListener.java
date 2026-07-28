package com.sh.aicommerce.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class JobListener implements JobExecutionListener {

    private final JobLauncher jobLauncher;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Reservation Batch Job Start");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        if (jobExecution.getStatus().isUnsuccessful()) {
            log.error("Reservation Batch Job Fail");
        }
    }
}