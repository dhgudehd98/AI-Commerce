package com.sh.aicommerce.common.exception.toss;

public class TossCancelException extends RuntimeException{
    private final String errorCode;

    public TossCancelException(String message) {
        this(null, message, null);
    }

    public TossCancelException(String message, Throwable cause) {
        this(null, message, cause);
    }

    public TossCancelException(
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