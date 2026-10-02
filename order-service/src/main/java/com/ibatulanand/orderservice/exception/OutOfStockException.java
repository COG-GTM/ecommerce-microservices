package com.ibatulanand.orderservice.exception;

import java.util.List;

public class OutOfStockException extends RuntimeException {
    private final List<String> skuCodes;

    public OutOfStockException(List<String> skuCodes) {
        super("Products out of stock: " + String.join(", ", skuCodes));
        this.skuCodes = skuCodes;
    }

    public List<String> getSkuCodes() {
        return skuCodes;
    }
}
