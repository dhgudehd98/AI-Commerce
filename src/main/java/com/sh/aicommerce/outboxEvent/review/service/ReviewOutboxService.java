package com.sh.aicommerce.outboxEvent.review.service;

import com.sh.aicommerce.common.exception.outbox.OutboxException;
import com.sh.aicommerce.entity.ReviewOutboxEvent;
import com.sh.aicommerce.enums.review.reviewEvent.OutboxPublishStatus;
import com.sh.aicommerce.outboxEvent.redis.ReviewOutboxPublisher;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxService {

    private final ReviewOutBoxEventRepository eventRepository;

    @Transactional
    public List<Long> publishReviewEvents() {

        log.info("[ReviewOutboxEvent] ReviewOutboxEvent 임베딩 대상 조회 요청");
        // ReviewOutboxEvent에 저장된 임베딩 할 데이터 조회
        List<ReviewOutboxEvent> events = eventRepository.findPublishingTargets();

        events.forEach(ReviewOutboxEvent::publishing);

        return events.stream()
                .map(ReviewOutboxEvent::getId)
                .toList();
    }

    @Transactional
    public void markPublished(Long outboxId) {
        ReviewOutboxEvent event = eventRepository.findById(outboxId).orElseThrow(() -> new OutboxException("ReviewOutboxEvent가 존재하지 않습니다."));
        event.markPublished();
    }

    @Transactional
    public void markFailed(Long outboxId, String failureCode) {
        ReviewOutboxEvent event = eventRepository.findById(outboxId).orElseThrow(() -> new OutboxException("ReviewOutboxEvent가 존재하지 않습니다."));
        event.markFailed(failureCode);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recoverExpiredPublishingEvents() {
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(30);
        List<ReviewOutboxEvent> events = eventRepository.findExpiredPublishingReviewEvents(expiredAt);

        if(events.size() <= 0) return;

        for (ReviewOutboxEvent event : events) {
            // ReviewOutboxEvent Status의 값이 Publishing이 아니면 종료
            if(!event.getPublishStatus().equals(OutboxPublishStatus.PUBLISHING)) continue;

            // 재시도 횟수가 3회를 초과하면  상태 전이 : publishing -> FAIL로 변경
            if (event.getPublishAttemptCount() >= 3) {
                log.info("[Review Outbox Publishing Timeout] reviewId : {}", event.getReview_id());
                event.markFailed("PUBLISHING_TIMEOUT");
            }
            // 재시도 횟수가 3회를 초과하지 않으면 상태 전이 : publishing -> pending 변경 할 수 있는지 확인
            else{
                log.info("[Review Outbox Publishing] : 재시도 3회 미만 Publishing 상태 데이터 재발행 요청 outboxId : {}", event.getId());
                event.recoverPending();
            }
        }


    }
}