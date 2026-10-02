package com.ibatulanand.orderservice.exception;

public class OrderValidationException extends RuntimeException {
    public OrderValidationException(String message) {
        super(message);
    }
}
