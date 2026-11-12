package com.sh.aicommerce.batch.transactionReconciliation.config;

import com.sh.aicommerce.batch.transactionReconciliation.listener.PgTransactionReconciliationJobListener;
import com.sh.aicommerce.batch.transactionReconciliation.reader.PgTransactionReconciliationReader;
import com.sh.aicommerce.batch.transactionReconciliation.writer.PgTransactionReconciliationWriter;
import com.sh.aicommerce.entity.PgTransaction;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationRunRepository;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationResultRepository;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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


@Configuration
@RequiredArgsConstructor
@Slf4j
public class PgTransactionReconciliationJobConfig {
    private final PlatformTransactionManager transactionManager;

    private final PaymentRepository paymentRepository;
    private final JobRepository jobRepository;
    private final PgTransactionRepository transactionRepository;
    private final PaymentCancellationRepository cancellationRepository;

    @Bean
    public Job pgTransactionReconciliationJob(
            Step pgTransactionReconciliationStep,
            PgTransactionReconciliationJobListener listener
    ) {
        return new JobBuilder("pgTransactionReconciliationJob", jobRepository)
                .listener(listener)
                .start(pgTransactionReconciliationStep)
                .build();

    }


    @Bean
    public Step pgTransactionReconciliationStep(
            ItemReader<PgTransaction> pgTransactionReconciliationReader,
//            ItemProcessor<PgTransaction, PgTransaction> pgTransactionReconciliationProcessor,
            ItemWriter<PgTransaction> pgTransactionReconciliationWriter
    ) {
        return new StepBuilder("pgTransactionReconciliationStep", jobRepository)
                .<PgTransaction, PgTransaction>chunk(50, transactionManager)
                .reader(pgTransactionReconciliationReader)
//                .processor(pgTransactionReconciliationProcessor)
                .writer(pgTransactionReconciliationWriter)
                .build();
    }

    @Bean
    @StepScope
    public PgTransactionReconciliationReader pgTransactionReconciliationReader(
            @Value("#{jobExecutionContext['collectionRunId']}") Long collectionRunId
    ){
        return new PgTransactionReconciliationReader(collectionRunId, transactionRepository);
    }

    @Bean
    @StepScope
    public PgTransactionReconciliationWriter pgTransactionReconciliationWriter(
            @Value("#{jobExecutionContext['collectionRunId']}") Long collectionRunId,
            @Value("#{jobExecutionContext['reconciliationRunId']}") Long reconciliationRunId,
            PaymentLedgerRepository ledgerRepository,
            PgTransactionReconciliationRunRepository runRepository,
            PgTransactionReconciliationResultRepository resultRepository,
            PgTransactionReconciliationIssueRepository issueRepository,
            ObjectMapper objectMapper
    ) {
        return new PgTransactionReconciliationWriter(collectionRunId, reconciliationRunId,
                paymentRepository, ledgerRepository, runRepository, resultRepository, issueRepository, cancellationRepository, objectMapper);
    }


}