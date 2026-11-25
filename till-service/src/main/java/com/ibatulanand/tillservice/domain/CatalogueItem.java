package com.ibatulanand.tillservice.domain;

/**
 * A sellable item as the till sees it. Prices are GBP integer pence; for an item sold by weight
 * {@code pricePence} is the price per kg.
 */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                            boolean soldByWeight) {

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
        this(barcode, skuCode, name, pricePence, quickKey, false);
    }
}
