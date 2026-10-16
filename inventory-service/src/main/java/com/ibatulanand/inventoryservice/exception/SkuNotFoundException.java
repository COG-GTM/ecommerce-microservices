package com.ibatulanand.inventoryservice.exception;

public class SkuNotFoundException extends RuntimeException {
    public SkuNotFoundException(String skuCode) {
        super("SKU " + skuCode + " was not found");
    }
}
