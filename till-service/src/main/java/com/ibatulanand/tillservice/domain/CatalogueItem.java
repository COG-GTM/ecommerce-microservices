package com.ibatulanand.tillservice.domain;

/** A sellable item as the till sees it. Prices are GBP integer pence, VAT inclusive; vatCode is Z or S. */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                            String vatCode) {

    /** An item with no VAT code from the catalogue; VAT analysis treats it as standard-rated. */
    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
        this(barcode, skuCode, name, pricePence, quickKey, null);
    }
}
