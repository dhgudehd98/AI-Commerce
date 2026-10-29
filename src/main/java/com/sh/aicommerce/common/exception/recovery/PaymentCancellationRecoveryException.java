package com.sh.aicommerce.common.exception.recovery;

public class PaymentCancellationRecoveryException extends RuntimeException{
    public PaymentCancellationRecoveryException(String message) {
        super(message);
    }
}