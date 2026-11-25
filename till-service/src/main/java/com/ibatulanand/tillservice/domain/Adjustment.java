package com.ibatulanand.tillservice.domain;

import java.util.List;

/**
 * A basket-level price adjustment produced by a {@link com.ibatulanand.tillservice.pricing.PricingRule}.
 * Negative amountPence is a saving (multi-buy, loyalty); positive is a charge. lineId is the line the till shows it
 * under; lineIds are every line it was earned on (a meal deal's main, side and drink), defaulting to lineId.
 */
public record Adjustment(String code, String label, int amountPence, String lineId, List<String> lineIds) {

    public Adjustment {
        lineIds = lineIds != null && !lineIds.isEmpty() ? List.copyOf(lineIds)
                : lineId == null ? List.of() : List.of(lineId);
    }

    public Adjustment(String code, String label, int amountPence, String lineId) {
        this(code, label, amountPence, lineId, null);
    }
}
