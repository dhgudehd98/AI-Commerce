package com.sh.aicommerce.entity;


import com.sh.aicommerce.enums.review.reviewEvent.ReviewEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt; // 이벤트 발생 시각

    @Column(name = "published_at")
    private LocalDateTime publishedAt; // Redis Stream 발행 완료 시각

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount; // Redis 발행 시도 횟수

    @Column(name = "last_failure_code", length = 100)
    private String lastFailureCode; // 마지막 발행 실패 원인
}