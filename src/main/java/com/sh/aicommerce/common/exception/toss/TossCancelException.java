package com.sh.aicommerce.common.exception.toss;

public class TossCancelException extends RuntimeException{
    public TossCancelException(String message) {
        super(message);
    }

    public TossCancelException(String message, Throwable cause) {
        super(message, cause);
    }
}