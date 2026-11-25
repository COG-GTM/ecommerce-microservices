package com.ibatulanand.tillservice.client;

import com.ibatulanand.tillservice.domain.Basket;

/** Persists a completed sale (order-service), which publishes the order event to Kafka. */
public interface OrderClient {

    /** @return the order number assigned by order-service */
    String placeSale(Basket basket, String storeNumber, String tenderType);

    /**
     * Same as {@link #placeSale(Basket, String, String)}, but order-service stores at most one order per
     * idempotency key, so replaying a queued offline sale returns the order already stored.
     */
    default String placeSale(Basket basket, String storeNumber, String tenderType, String idempotencyKey) {
        return placeSale(basket, storeNumber, tenderType);
    }
}
