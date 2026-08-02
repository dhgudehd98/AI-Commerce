package com.sh.aicommerce.enums.review.reviewEvent;

public enum OutboxPublishStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    RETRY_WAIT,
    FAILED
}