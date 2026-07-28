package com.sh.aicommerce.batch.config;

import com.sh.aicommerce.batch.listener.JobListener;
import com.sh.aicommerce.batch.reader.ExpiredPaymentReader;
import com.sh.aicommerce.batch.writer.ExpiredPaymentWriter;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.payment.service.PaymentTransactionService;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

@Configuration
@EnableBatchProcessing
@Slf4j
@RequiredArgsConstructor
public class ExpiredPaymentBatchConfig {

    private final PaymentTransactionService transactionService;
    private final PlatformTransactionManager transaction;
    private final JobListener jobListener;
    private final JobRepository jobRepository;

    @Bean
    public Job expiredPaymentJob(Step paymentStep) {
        return new JobBuilder("expiredPaymentJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(paymentStep)
                .build();
    }

    @Bean
    public Step paymentStep(
            JpaPagingItemReader<Long> expiredPaymentReader,
            ItemWriter<Long> expiredPaymentWriter
    ) {
        return new StepBuilder("expiredPaymentJob", jobRepository)
                .<Long, Long>chunk(5, transaction)
                .reader(expiredPaymentReader)
//                .processor(reservationProcessor())
                .writer(expiredPaymentWriter)
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<Long> expiredPaymentReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['expiredAt']}") LocalDateTime expiredAt
    ) {
        ExpiredPaymentReader reader = new ExpiredPaymentReader();
        return reader.expiredPaymentRead(entityManagerFactory, expiredAt);
    }

    @Bean
    @StepScope
    public ItemWriter<Long> expiredPaymentWriter(
            @Value("#{jobParameters['expiredAt']}") LocalDateTime expiredAt
    ) {
        return new ExpiredPaymentWriter(transactionService, expiredAt);
    }
}