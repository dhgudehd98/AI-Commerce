package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.common.exception.outbox.OutboxException;
import com.sh.aicommerce.entity.ReviewOutboxEvent;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import com.sh.aicommerce.outboxEvent.review.service.ReviewOutboxService;
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

    private static final String STREAM_NAME = "review:embedding:stream";
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewOutboxService outboxService;
    private final ReviewOutBoxEventRepository outBoxEventRepository;

//    @Scheduled(cron = "0 0 * * * *")
    public void publish() {
        List<Long> outboxes = outboxService.publishReviewEvents();

        // publishing 할 데이터가 존재하지 않으면 스케줄러 종료
        if(outboxes.size() <= 0) return;

        // OutboxId 발행하여 해당 리뷰의 리뷰의 정보를 Redis에 저장
        for (Long outboxIds : outboxes) {
            log.info("[Review Outbox] Publishing ReviewOutboxId : {}", outboxIds);
            publishOne(outboxIds);
        }
    }

    public void publishOne(Long outboxId) {
        log.info("[ReviewOutbox 발행 요청] outboxId : {}", outboxId);
        ReviewOutboxEvent event = outBoxEventRepository.findById(outboxId).orElseThrow(() -> new OutboxException("ReviewOutboxEvent의 저장되어 있는 이벤트를 찾을 수 없습니다."));

        try {
            stringRedisTemplate.opsForStream().add(
                    STREAM_NAME,
                    Map.of(
                            "eventId", event.getEventId(),
                            "type", event.getEventType().name(),
                            "reviewId", event.getReview_id().toString(),
                            "optionId", event.getProductOptionId().toString(),
                            "occurredAt", event.getOccurredAt().toString()
                    )
            );

            // ReveiwOutbox Status -> PUBLISHED로 변경
            outboxService.markPublished(outboxId);
            log.info("[ReviewOutbox Published] Publish outboxId : {}", outboxId);
        } catch (Exception e) {
            log.error("[Review Outbox FAILED] FAIL outboxId : {}", outboxId);
            // ReveiwOutbox Status -> FAILED로 변경
            outboxService.markFailed(outboxId,"REVIEW_OUTBOX_PUBLISHED_FAIL");
        }
    }
}
