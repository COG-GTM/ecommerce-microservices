package com.ibatulanand.tillservice.domain;

/**
 * A basket-level price adjustment produced by a {@link com.ibatulanand.tillservice.pricing.PricingRule}.
 * Negative amountPence is a saving (multi-buy, loyalty); positive is a charge.
 */
public record Adjustment(String code, String label, int amountPence, String lineId) {
}
