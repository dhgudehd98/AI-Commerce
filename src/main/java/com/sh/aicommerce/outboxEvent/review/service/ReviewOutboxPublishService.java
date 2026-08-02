package com.sh.aicommerce.outboxEvent.review.service;

import com.sh.aicommerce.common.exception.outbox.OutboxException;
import com.sh.aicommerce.entity.ReviewOutboxEvent;
import com.sh.aicommerce.enums.review.reviewEvent.OutboxPublishStatus;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxPublishService {

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
}