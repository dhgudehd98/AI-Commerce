package com.sh.aicommerce.batch.config;

import com.sh.aicommerce.batch.transactionHistory.listener.PgTransactionCollectionJobListener;
import org.springframework.batch.core.JobParametersInvalidException;
import com.sh.aicommerce.batch.transactionHistory.reader.PgTransactionReader;
import com.sh.aicommerce.batch.transactionHistory.writer.PgTransactionWriter;
import com.sh.aicommerce.toss.dto.transaction.TransactionResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class PgTransactionCollectionJobConfig {

    private final PlatformTransactionManager transactionManager;
    private final TossPaymentClient client;

    private final PgTransactionRepository transactionRepository;
    private final JobRepository jobRepository;

    @Bean
    public Job pgTransactionCollectionJob(Step pgTransactionCollectionStep,
                                           PgTransactionCollectionJobListener listener) {
        return new JobBuilder("pgTransactionCollectionJob", jobRepository)
                .validator(parameters -> {
                    try {
                        LocalDate.parse(parameters.getString("targetDate"));
                    } catch (RuntimeException e) {
                        throw new JobParametersInvalidException("targetDate는 yyyy-MM-dd 형식의 필수 값입니다.");
                    }
                })
                .listener(listener)
                .start(pgTransactionCollectionStep)
                .build();
    }

    @Bean
    public Step pgTransactionCollectionStep(
            ItemReader<TransactionResponseDto> pgTransactionItemReader,
            ItemWriter<TransactionResponseDto> pgTransactionItemWriter
    ) {
        return new StepBuilder("pgTransactionCollectionStep", jobRepository)
                .<TransactionResponseDto, TransactionResponseDto>chunk(50, transactionManager)
                .reader(pgTransactionItemReader)
                .writer(pgTransactionItemWriter)
                .build();
    }

    @Bean
    @StepScope
    public PgTransactionReader pgTransactionItemReader(
            @Value("#{jobParameters['targetDate']}")String targetDate,
            TossPaymentClient client
    ){
        LocalDate date = LocalDate.parse(targetDate);

        LocalDateTime startDate = date.atStartOfDay();
        LocalDateTime endDate = date.atTime(23, 59, 59);

        return new PgTransactionReader(client, startDate, endDate);
    }

    @Bean
    @StepScope
    public PgTransactionWriter pgTransactionItemWriter(
            @Value("#{jobExecutionContext['collectionRunId']}") Long collectionRunId) {
        return new PgTransactionWriter(transactionRepository, collectionRunId);
    }
}