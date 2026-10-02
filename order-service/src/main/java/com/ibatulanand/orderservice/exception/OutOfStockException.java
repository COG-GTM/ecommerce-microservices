package com.ibatulanand.orderservice.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class OutOfStockException extends IllegalArgumentException {
    private final List<String> skuCodes;

    public OutOfStockException(List<String> skuCodes) {
        super("Product is not in stock, please try again later");
        this.skuCodes = List.copyOf(skuCodes);
    }
}
