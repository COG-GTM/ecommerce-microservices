package com.ibatulanand.orderservice.service;

import java.util.List;

/** A standard (non-till) order contains an age-restricted SKU, which only a till sale with a Challenge 25 check may sell. */
public class AgeRestrictedItemException extends RuntimeException {

    private final List<String> skuCodes;

    public AgeRestrictedItemException(List<String> skuCodes) {
        super("Age-restricted items can only be sold at a till: " + String.join(", ", skuCodes));
        this.skuCodes = List.copyOf(skuCodes);
    }

    public List<String> getSkuCodes() {
        return skuCodes;
    }
}
