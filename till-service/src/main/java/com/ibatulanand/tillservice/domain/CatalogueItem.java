package com.ibatulanand.tillservice.domain;

/**
 * A sellable item as the till sees it. Prices are GBP integer pence, VAT inclusive; vatCode is Z or S. For an item
 * sold by weight {@code pricePence} is the price per kg. minimumAge is null for unrestricted items; otherwise the
 * till must approve a Challenge 25 check before payment.
 */
public record CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                            String vatCode, boolean soldByWeight, Integer minimumAge) {

    /** An item with no VAT code from the catalogue; VAT analysis treats it as standard-rated. */
    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey) {
        this(barcode, skuCode, name, pricePence, quickKey, null, false, null);
    }

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                         String vatCode) {
        this(barcode, skuCode, name, pricePence, quickKey, vatCode, false, null);
    }

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                         boolean soldByWeight) {
        this(barcode, skuCode, name, pricePence, quickKey, null, soldByWeight, null);
    }

    public CatalogueItem(String barcode, String skuCode, String name, int pricePence, boolean quickKey,
                         Integer minimumAge) {
        this(barcode, skuCode, name, pricePence, quickKey, null, false, minimumAge);
    }

    public boolean ageRestricted() {
        return minimumAge != null && minimumAge > 0;
    }
}
