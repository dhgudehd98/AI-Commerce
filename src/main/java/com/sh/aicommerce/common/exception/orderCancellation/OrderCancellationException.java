package com.sh.aicommerce.common.exception.orderCancellation;

public class OrderCancellationException extends RuntimeException{
    public OrderCancellationException(String message) {
        super(message);
    }
}