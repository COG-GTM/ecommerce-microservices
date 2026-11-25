package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;

import java.util.List;

/**
 * Extension point for basket pricing (multi-buys, More Card, ...).
 * Each rule is a Spring bean; {@link BasketPricer} applies every rule after line totals are computed, then derives the
 * VAT bands from the priced basket with {@link VatAnalysis}, so rules never produce VAT themselves.
 * Rules must be pure: read the basket, return adjustments, never mutate it.
 */
public interface PricingRule {

    List<Adjustment> apply(Basket basket);
}
