package com.ibatulanand.tillservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Store/till identity shown in the till header. Bound from till.* properties. */
@ConfigurationProperties(prefix = "till")
public record TillProperties(String storeNumber, String storeName, String defaultTillId, String operatorId,
                             String operatorName, String productServiceUrl, String inventoryServiceUrl,
                             String orderServiceUrl) {

    public TillProperties {
        storeNumber = orDefault(storeNumber, "0417");
        storeName = orDefault(storeName, "Bradford Demo");
        defaultTillId = orDefault(defaultTillId, "07");
        operatorId = orDefault(operatorId, "OP-1001");
        operatorName = orDefault(operatorName, "Demo Operator");
        productServiceUrl = orDefault(productServiceUrl, "http://product-service");
        inventoryServiceUrl = orDefault(inventoryServiceUrl, "http://inventory-service");
        orderServiceUrl = orDefault(orderServiceUrl, "http://order-service");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
