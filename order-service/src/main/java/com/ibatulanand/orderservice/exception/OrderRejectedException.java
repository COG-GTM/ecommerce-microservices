package com.ibatulanand.orderservice.exception;

/**
 * The order cannot be fulfilled as requested (unknown product, insufficient
 * stock, ...). This is a client error, not a downstream failure, so it must not
 * trip the circuit breaker, be retried, or be masked by the fallback.
 */
public class OrderRejectedException extends RuntimeException {
    public OrderRejectedException(String message) {
        super(message);
    }
}
