package com.sh.aicommerce.common.exception.toss;

public class TossRestClientException extends RuntimeException{
    public TossRestClientException(String message) {
        super(message);
    }

    public TossRestClientException(String message, Throwable cause) {
        super(message, cause);
    }
}