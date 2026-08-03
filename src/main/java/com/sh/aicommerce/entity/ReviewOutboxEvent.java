package com.sh.aicommerce.entity;


import com.sh.aicommerce.enums.review.reviewEvent.OutboxPublishStatus;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

@Entity
@Table(
        name = "review_outbox_event",
        indexes = {
                @Index(
                        name = "idx_review_outbox_unpublished",
                        columnList = "published_at, occurred_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_outbox_event_id",
                        columnNames = "event_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewOutboxEvent {

    private static final Integer MAX_RETRY_COUNT = 3;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId; // 이벤트 중복 처리 식별자

    @Column(name = "review_id", nullable = false)
    private Long review_id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ReviewEventType eventType; //이벤트 발생 종류 -> CREATE, UPDATE, DELETE

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 20)
    private OutboxPublishStatus publishStatus;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt; // 이벤트 발생 시각

    @Column(name = "published_at")
    private LocalDateTime publishedAt; // Redis Stream 발행 완료 시각

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount; // Redis 발행 시도 횟수

    @Column(name = "last_failure_code", length = 100)
    private String lastFailureCode; // 마지막 발행 실패 원인

    public static ReviewOutboxEvent createEvent(Review review, Long productOptionId) {
        ReviewOutboxEvent event = new ReviewOutboxEvent();

        event.eventId = UUID.randomUUID().toString();
        event.review_id = review.getId();
        event.productOptionId = productOptionId;
        event.publishStatus = OutboxPublishStatus.PENDING;
        event.eventType = ReviewEventType.CREATED;
        event.occurredAt = LocalDateTime.now();
        event.publishAttemptCount = 0;

        return event;
    }

    public void recoverPending() {
        this.publishStatus = OutboxPublishStatus.PENDING;
        this.processingStartedAt = null;
    }

    public void markPublished() {
        this.publishStatus = OutboxPublishStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastFailureCode = null;
    }

    public void publishing() {
        this.publishStatus = OutboxPublishStatus.PUBLISHING;
        this.processingStartedAt = LocalDateTime.now();
        this.publishAttemptCount++;
    }

    public void markFailed(String lastFailureCode) {
        this.publishStatus = publishAttemptCount >= MAX_RETRY_COUNT
                ? OutboxPublishStatus.FAILED
                : OutboxPublishStatus.PENDING;
        this.lastFailureCode = lastFailureCode;
        this.processingStartedAt = null;
    }
}