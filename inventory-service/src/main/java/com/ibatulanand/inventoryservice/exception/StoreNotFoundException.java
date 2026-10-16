package com.ibatulanand.inventoryservice.exception;

public class StoreNotFoundException extends RuntimeException {
    public StoreNotFoundException(String storeId) {
        super("Store " + storeId + " was not found");
    }
}
