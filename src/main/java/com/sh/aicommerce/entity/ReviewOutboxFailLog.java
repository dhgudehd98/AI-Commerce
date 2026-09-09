package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "review_outbox_index_fail_log",
        uniqueConstraints = {
                @UniqueConstraint(
                        name =
                                "uk_review_outbox_index_fail_log_message_id",
                        columnNames = "message_id"
                )
        }
)
public class ReviewOutboxFailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_index_fail_log_id")
    private Long id;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30)
    private ReviewEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", nullable = false, length = 50)
    private ReviewEmbeddingFailureCode failureCode;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    public static ReviewOutboxFailLog create(
            String eventId,
            Long reviewId,
            String messageId,
            ReviewEventType eventType,
            ReviewEmbeddingFailureCode failureCode
    ) {
        ReviewOutboxFailLog failLog =
                new ReviewOutboxFailLog();

        failLog.eventId = eventId;
        failLog.reviewId = reviewId;
        failLog.messageId = messageId;
        failLog.eventType = eventType;
        failLog.failureCode = failureCode;
        failLog.failedAt = LocalDateTime.now();

        return failLog;
    }

    public static ReviewOutboxFailLog createMissingMessage(String messageId, ReviewEmbeddingFailureCode failureCode) {

        ReviewOutboxFailLog failLog = new ReviewOutboxFailLog();

        failLog.messageId = messageId;
        failLog.failureCode = failureCode;
        failLog.failedAt = LocalDateTime.now();

        return failLog;
    }
}