package com.ibatulanand.tillservice.domain;

/** A sellable item as the till sees it. Prices are GBP integer pence. */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
}
