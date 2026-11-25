package com.ibatulanand.productservice.pricing;

/** One item price as a price source publishes it. Prices are GBP integer pence. */
public record PriceRecord(String barcode, String skuCode, int pricePence) {
}
