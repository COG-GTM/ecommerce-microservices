package com.ibatulanand.tillservice.domain;

/**
 * A sellable item as the till sees it. Prices are GBP integer pence, VAT inclusive; vatCode is Z or S. For an item
 * sold by weight {@code pricePence} is the price per kg.
 */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                            String vatCode, boolean soldByWeight) {

    /** An item with no VAT code from the catalogue; VAT analysis treats it as standard-rated. */
    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
        this(barcode, skuCode, name, pricePence, quickKey, null, false);
    }

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                         String vatCode) {
        this(barcode, skuCode, name, pricePence, quickKey, vatCode, false);
    }

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                         boolean soldByWeight) {
        this(barcode, skuCode, name, pricePence, quickKey, null, soldByWeight);
    }
}
