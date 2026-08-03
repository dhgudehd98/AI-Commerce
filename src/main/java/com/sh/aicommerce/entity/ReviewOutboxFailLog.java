package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "review_outbox_index_fail_log",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_outbox_index_fail_log_message_id",
                        columnNames = "message_id"
                )
        }
)
@Getter
public class ReviewOutboxFailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_index_fail_log_id")
    private Long id;

    private Long reviewId;

    @Column(nullable = false)
    private String messageId;

    public ReviewOutboxFailLog(String messageId, String failReason) {
        this.messageId = messageId;
        this.failReason = failReason;
    }

    @Column(nullable = false)
    private String failReason;
    private String action;

    public ReviewOutboxFailLog(Long reviewId, String messageId, String failReason, String action) {
        this.reviewId = reviewId;
        this.messageId = messageId;
        this.failReason = failReason;
        this.action = action;
    }
}