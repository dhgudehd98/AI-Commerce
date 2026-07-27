package com.sh.aicommerce.batch.job;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ExpiredPaymentJob {

    private final JobLauncher launcher;
    private final Job expiredPaymentJob;

//    @Scheduled(cron = "0 0 * * * *")
    public void setExpiredPaymentJob() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        LocalDateTime expiredAt = LocalDateTime.now().minusHours(1);
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addLocalDateTime("expiredAt", expiredAt)
                .toJobParameters();

        log.info("Job Instance : {} ", expiredPaymentJob);

        launcher.run(expiredPaymentJob, jobParameters);
    }
}