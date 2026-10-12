package com.sh.aicommerce.common.exception.wms;

public class OutboundNotFoundException extends RuntimeException{

    public OutboundNotFoundException(String message) {
        super(message);
    }
}