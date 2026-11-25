package com.ibatulanand.productservice.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Price source feature flag. {@code pricing.source=legacy-file} keeps the nightly file import;
 * {@code pricing.source=price-feed} retires it and accepts prices through the REST feed.
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(PriceSource source, String legacyFile) {

    public PricingProperties {
        source = source == null ? PriceSource.LEGACY_FILE : source;
        legacyFile = legacyFile == null || legacyFile.isBlank() ? "classpath:legacy/PRICEFILE.DAT" : legacyFile;
    }
}
