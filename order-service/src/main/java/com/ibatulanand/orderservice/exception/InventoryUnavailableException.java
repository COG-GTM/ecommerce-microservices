package com.ibatulanand.orderservice.exception;

public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(Throwable cause) {
        super("Oops! Something went wrong, please order after some time!", cause);
    }
}
