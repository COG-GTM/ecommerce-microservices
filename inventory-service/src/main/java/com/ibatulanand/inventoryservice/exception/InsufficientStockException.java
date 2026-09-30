package com.ibatulanand.inventoryservice.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class InsufficientStockException extends RuntimeException {
    private final List<String> unavailableSkuCodes;

    public InsufficientStockException(List<String> unavailableSkuCodes) {
        super("Insufficient stock for: " + String.join(", ", unavailableSkuCodes));
        this.unavailableSkuCodes = List.copyOf(unavailableSkuCodes);
    }
}
