package com.ibatulanand.tillservice.client;

/** Stock checks (inventory-service). */
public interface StockClient {

    boolean isInStock(String skuCode);
}
