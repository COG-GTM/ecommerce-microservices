package com.ibatulanand.orderservice.exception;

public class InvalidOrderException extends OrderRejectedException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
