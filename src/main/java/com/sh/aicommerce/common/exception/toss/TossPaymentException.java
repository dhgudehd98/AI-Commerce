package com.sh.aicommerce.common.exception.toss;

import lombok.Getter;

@Getter
public class TossPaymentException extends RuntimeException{
    private final String errorCode;

    public TossPaymentException(String message) {
        this(null, message, null);
    }

    public TossPaymentException(String message, Throwable cause) {
        this(null, message, cause);
    }

    public TossPaymentException(
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}