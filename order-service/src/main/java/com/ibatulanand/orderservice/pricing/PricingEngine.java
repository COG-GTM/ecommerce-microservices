package com.ibatulanand.orderservice.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Server-side port of the POS {@code calculateTotals} (pos-webapp/src/lib/totals.ts).
 * Rounding happens at exactly the same steps as the POS; intermediate sums are exact.
 * The POS rounds binary doubles via {@code Math.round((v + EPSILON) * 100) / 100}, so a value
 * that is exactly on a half cent can round down there while HALF_UP rounds it up here.
 */
public final class PricingEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public OrderTotals calculate(List<PricingLine> lines,
                                 List<PromotionRule> promotions,
                                 BigDecimal servicesAndFees,
                                 BigDecimal taxRate,
                                 boolean taxExempt) {
        BigDecimal merchandise = BigDecimal.ZERO;
        BigDecimal markdown = BigDecimal.ZERO;
        BigDecimal promotionEligible = BigDecimal.ZERO;
        for (PricingLine line : lines) {
            BigDecimal qty = BigDecimal.valueOf(line.quantity());
            merchandise = merchandise.add(line.listPrice().multiply(qty));
            markdown = markdown.add(line.listPrice().subtract(line.unitPrice()).multiply(qty));
            if (!line.finalSale()) {
                promotionEligible = promotionEligible.add(line.unitPrice().multiply(qty));
            }
        }
        BigDecimal merchandiseTotal = round2(merchandise);

        BigDecimal promotionTotal = BigDecimal.ZERO;
        for (PromotionRule promo : promotions) {
            BigDecimal percentPart = promotionEligible.multiply(promo.percentOff()).divide(HUNDRED);
            promotionTotal = promotionTotal.add(percentPart).add(promo.amountOff());
        }

        BigDecimal discountTotal = round2(markdown.add(promotionTotal));
        BigDecimal taxableSubtotal = round2(merchandiseTotal.subtract(discountTotal).add(servicesAndFees));
        BigDecimal salesTax = taxExempt ? round2(BigDecimal.ZERO) : round2(taxableSubtotal.multiply(taxRate));
        BigDecimal total = round2(taxableSubtotal.add(salesTax));

        return new OrderTotals(
                merchandiseTotal,
                round2(servicesAndFees),
                discountTotal,
                taxableSubtotal,
                taxRate,
                salesTax,
                total,
                discountTotal,
                taxExempt);
    }

    public static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
