package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.outboxEvent.review.service.ReviewOutboxPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxIndexConsumer implements ApplicationRunner {

    private final ReviewEmbeddingProcessor processor;
    private final ReviewOutboxPublishService outboxPublishService;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_NAME = "review:embedding:stream";
    private static final String GROUP_NAME = "reviewEvent-group";

    @Value("${redis.stream.review.consumer.group}")
    private String CONSUMER_NAME;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initStream();
        container.receive(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed()),
                this::handleReview
        );
    }

    private void initStream() {
        try {
            log.info("[Review Index Stream & Consumer group Create");
            redisTemplate.opsForStream()
                    .createGroup(STREAM_NAME, ReadOffset.from("0"), GROUP_NAME);
        } catch (Exception e) {
            if (isBusyGroupException(e)) {
                log.info("[Consumer Group 이미 존재]");
                return;
            }

            log.error("[Consumer Group 생성 실패]", e);
            throw e;
        }
    }

    private boolean isBusyGroupException(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    public void handleReview(MapRecord<String, String, String> message) {
        String messageId = message.getId().getValue();
        Long reviewId = Long.parseLong(message.getValue().get("reviewId"));

        try {
            processor.processEmbedding(reviewId);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            log.info("[리뷰 임베딩 완료] : reviewId : {}", reviewId);
        } catch (Exception e) {
            log.error(
                    "[리뷰 Embedding 실패] : messageId = {}", messageId
            );
            outboxPublishService.markRetry(reviewId);
        }
    }
}