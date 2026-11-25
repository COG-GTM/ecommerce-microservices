package com.ibatulanand.tillservice.client;

import com.ibatulanand.tillservice.domain.Basket;

/** Persists a completed sale (order-service), which publishes the order event to Kafka. */
public interface OrderClient {

    /** @return the order number assigned by order-service */
    String placeSale(Basket basket, String storeNumber, String tenderType);
}
