package com.sh.aicommerce.common.exception.outbox.review;

import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import lombok.Getter;

@Getter
public class ReviewEmbeddingException extends RuntimeException{

    private final ReviewEmbeddingFailureCode failureCode;

    public ReviewEmbeddingException(
            ReviewEmbeddingFailureCode failureCode,
            Throwable cause
    ) {
        super(failureCode.name(), cause);
        this.failureCode = failureCode;
    }
}