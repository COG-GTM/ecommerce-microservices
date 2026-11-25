package com.ibatulanand.orderservice.exception;

/**
 * An order refused because of the request itself (bad input, unavailable stock).
 * These are caller outcomes, not inventory-service faults, so the inventory circuit breaker ignores them.
 */
public abstract class OrderRejectedException extends RuntimeException {

    protected OrderRejectedException(String message) {
        super(message);
    }
}
