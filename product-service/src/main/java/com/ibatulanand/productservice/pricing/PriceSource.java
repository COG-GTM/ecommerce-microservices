package com.ibatulanand.productservice.pricing;

/** Where catalogue prices come from. Selected with {@code pricing.source}. */
public enum PriceSource {
    /** Nightly fixed-width price file from the legacy price bridge. */
    LEGACY_FILE,
    /** REST price-feed endpoint ({@code PUT /api/product/price-feed}). */
    PRICE_FEED
}
