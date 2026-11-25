package com.ibatulanand.tillservice.domain;

/**
 * A sellable item as the till sees it. Prices are GBP integer pence. minimumAge is null for unrestricted items;
 * otherwise the till must approve a Challenge 25 check before payment.
 */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                            Integer minimumAge) {

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
        this(barcode, skuCode, name, pricePence, quickKey, null);
    }

    public boolean ageRestricted() {
        return minimumAge != null && minimumAge > 0;
    }
}
