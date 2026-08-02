package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.common.exception.outbox.OutboxException;
import com.sh.aicommerce.entity.ReviewOutboxEvent;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import com.sh.aicommerce.outboxEvent.review.service.ReviewOutboxPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxPublisher {

    private static final String STREAM_KEY = "review:embedding:stream";
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewOutboxPublishService publishService;
    private final ReviewOutBoxEventRepository outBoxEventRepository;

//    @Scheduled(cron = "0 0 * * * *")
    public void publish() {
        List<Long> outboxes = publishService.publishReviewEvents();

        // OutboxId 발행하여 해당 리뷰의 리뷰의 정보를 Redis에 저장
        for (Long outboxIds : outboxes) {
            publishOne(outboxIds);
        }
    }

    private void publishOne(Long outboxId) {
        ReviewOutboxEvent event = outBoxEventRepository.findById(outboxId).orElseThrow(() -> new OutboxException("ReviewOutboxEvent의 저장되어 있는 이벤트를 찾을 수 없습니다."));

        try {
            stringRedisTemplate.opsForStream().add(
                    STREAM_KEY,
                    Map.of(
                            "eventId", event.getEventId(),
                            "type", event.getEventType().name(),
                            "reviewId", event.getReview_id().toString(),
                            "optionId", event.getProductOptionId().toString(),
                            "occurredAt", event.getOccurredAt().toString()
                    )
            );

            // ReveiwOutbox Status -> PUBLISHED로 변경
            publishService.markPublished(outboxId);
        } catch (Exception e) {
            log.error("[ReviewOutbox Publisher] : OutboxEvent 발행 실패");

            // ReveiwOutbox Status -> FAILED로 변경
            publishService.markFailed(outboxId,"REDIS_STREM_WRITE_FAILED");
        }
    }
}