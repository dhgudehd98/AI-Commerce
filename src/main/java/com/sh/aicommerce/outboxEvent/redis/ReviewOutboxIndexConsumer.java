package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.outboxEvent.review.service.ReviewOutboxService;
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

import static com.sh.aicommerce.outboxEvent.review.config.ReviewStreamConstants.GROUP_NAME;
import static com.sh.aicommerce.outboxEvent.review.config.ReviewStreamConstants.STREAM_NAME;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxIndexConsumer implements ApplicationRunner {

    private final ReviewEmbeddingProcessor processor;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    @Value("${redis.stream.review.consumer-name}")
    private String consumerName;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initStream();
        container.receive(
                Consumer.from(GROUP_NAME, consumerName),
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
        }
    }
}
