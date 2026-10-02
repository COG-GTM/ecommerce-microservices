package com.ibatulanand.orderservice.pricing;

public enum PricingRoundingMode {
    /** Reproduces pos-webapp calculateTotals bit-for-bit using IEEE-754 doubles. */
    LEGACY_POS,
    /** Exact BigDecimal arithmetic with HALF_UP rounding at 2 decimals. */
    HALF_UP
}
