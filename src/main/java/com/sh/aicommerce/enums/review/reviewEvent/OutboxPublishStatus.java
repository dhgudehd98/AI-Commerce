package com.sh.aicommerce.enums.review.reviewEvent;

public enum OutboxPublishStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    RETRY_WAIT,
    FAILED
}