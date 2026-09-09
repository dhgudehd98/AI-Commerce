package com.sh.aicommerce.common.exception.outbox;

public class OutboxException extends RuntimeException{

    public OutboxException(String message) {
        super(message);
    }
}