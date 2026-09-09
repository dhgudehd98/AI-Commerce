package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.common.exception.outbox.review.ReviewEmbeddingException;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
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

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxIndexConsumer implements ApplicationRunner {

    private final ReviewEmbeddingProcessor processor;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private static final String FAILURE_KEY_PREFIX = "review:embedding:failure:";
    private static final String STREAM_NAME = "review:embedding:stream";
    private static final String GROUP_NAME = "reviewEvent-group";

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

        try {
            String reviewIdValue = message.getValue().get("reviewId");

            if (reviewIdValue == null) {
                saveFailureCode(
                        ReviewEmbeddingFailureCode.INVALID_STREAM_MESSAGE,
                        messageId
                );

                return;
            }
            Long reviewId = Long.parseLong(reviewIdValue);

            processor.processEmbedding(reviewId);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            // 기존에 실패 했던 작업이 성공적으로 진행되고 정상적으로 ACK 값이 날라가면 fail:index:messageId 삭제
            deleteFailureCode(messageId);
            log.info("[리뷰 임베딩 완료] : reviewId : {}", reviewId);

        } catch (ReviewEmbeddingException reviewEmbeddingException) {
            saveFailureCode(reviewEmbeddingException.getFailureCode(), messageId);

            log.error(
                    "[리뷰 Embedding 실패] messageId: {}, failureCode: {}",
                    messageId,
                    reviewEmbeddingException.getFailureCode(),
                    reviewEmbeddingException
            );
        } catch (Exception e) {
            saveFailureCode(
                    ReviewEmbeddingFailureCode.UNKNOWN_ERROR,
                    messageId
            );
            log.error(
                    "[리뷰 Embedding 실패] 알 수 없는 오류 에러 메세지 : {}",
                    e.getMessage()
            );
        }
    }

    private void deleteFailureCode(String messageId) {
        redisTemplate.delete(
                FAILURE_KEY_PREFIX + messageId
        );
    }

    private void saveFailureCode(ReviewEmbeddingFailureCode failureCode, String messageId) {
        String failStreamKey = FAILURE_KEY_PREFIX + messageId;
        try {
            redisTemplate.opsForValue().set(
                    failStreamKey,
                    failureCode.name(),
                    Duration.ofDays(7)
            );
        } catch (Exception e) {
            log.error("[Review failureCode 저장 실패] messageId: {},failureCode: {}",
            messageId,
                    failureCode,
                    e
            );
        }
    }
}
