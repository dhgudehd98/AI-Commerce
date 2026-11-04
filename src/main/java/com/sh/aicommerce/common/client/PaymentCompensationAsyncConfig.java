package com.sh.aicommerce.common.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PaymentCompensationAsyncConfig {
    @Bean(name = "paymentCompensationExecutor")
    public ThreadPoolTaskExecutor paymentCompensationExecutor(
            @Value("${payment.compensation.executor.pool-size:4}") int poolSize,
            @Value("${payment.compensation.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("payment-compensation-");
        // 포화 시 요청 스레드에서 PG 호출을 실행하지 않습니다. DB의 PENDING 작업은 보존됩니다.
        return executor;
    }
}
