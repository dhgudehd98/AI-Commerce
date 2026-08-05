package com.sh.aicommerce.enums.review.reviewEvent;

public enum ReviewEmbeddingFailureCode {
    REVIEW_SOURCE_READ_FAILED,
    INVALID_STREAM_MESSAGE,
    EMBEDDING_API_FAILED,
    ELASTICSEARCH_SAVE_FAILED,
    ELASTICSEARCH_DELETE_FAILED,
    STREAM_MESSAGE_NOTFOUND,
    UNKNOWN_ERROR
}
