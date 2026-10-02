package com.ibatulanand.orderservice.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Server-side port of the POS {@code calculateTotals} (pos-webapp/src/lib/totals.ts).
 *
 * {@link PricingRoundingMode#LEGACY_POS} reproduces the JS arithmetic bit-for-bit
 * using IEEE-754 doubles (Java double == JS Number) and the POS's
 * {@code Math.round((v + Number.EPSILON) * 100) / 100} rounding.
 * {@link PricingRoundingMode#HALF_UP} uses exact BigDecimal arithmetic and can
 * differ on values that land exactly on a half cent.
 */
public final class PricingEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PricingRoundingMode mode;

    public PricingEngine() {
        this(PricingRoundingMode.LEGACY_POS);
    }

    public PricingEngine(PricingRoundingMode mode) {
        this.mode = mode;
    }

    public OrderTotals calculate(List<PricingLine> lines,
                                 List<PromotionRule> promotions,
                                 BigDecimal servicesAndFees,
                                 BigDecimal taxRate,
                                 boolean taxExempt) {
        return mode == PricingRoundingMode.LEGACY_POS
                ? calculateLegacyPos(lines, promotions, servicesAndFees, taxRate, taxExempt)
                : calculateHalfUp(lines, promotions, servicesAndFees, taxRate, taxExempt);
    }

    /**
     * IEEE-754 double reproduction of pos-webapp/src/lib/totals.ts calculateTotals.
     * Every reduce mirrors the JS order, left to right, each accumulator starting
     * at 0.0. Inputs are converted via {@code Double.parseDouble(toPlainString())},
     * which is correctly rounded exactly like JSON.parse on the JS side.
     */
    private OrderTotals calculateLegacyPos(List<PricingLine> lines,
                                           List<PromotionRule> promotions,
                                           BigDecimal servicesAndFees,
                                           BigDecimal taxRate,
                                           boolean taxExempt) {
        double merchSum = 0.0;
        double markdown = 0.0;
        double eligible = 0.0;
        for (PricingLine line : lines) {
            double list = d(line.listPrice());
            double unit = d(line.unitPrice());
            double qty = line.quantity();
            merchSum += list * qty;
            markdown += (list - unit) * qty;
            if (!line.finalSale()) {
                eligible += unit * qty;
            }
        }
        double promotionTotal = 0.0;
        for (PromotionRule promo : promotions) {
            // JS `sum + (e*p)/100 + amt` is left-associative: ((sum + e*p/100) + amt)
            promotionTotal = promotionTotal + (eligible * d(promo.percentOff())) / 100 + d(promo.amountOff());
        }
        double fees = d(servicesAndFees);
        double merchandiseTotal = legacyRound2(merchSum);
        double discountTotal = legacyRound2(markdown + promotionTotal);
        double taxableSubtotal = legacyRound2((merchandiseTotal - discountTotal) + fees);
        double salesTax = taxExempt ? 0.0 : legacyRound2(taxableSubtotal * d(taxRate));
        double total = legacyRound2(taxableSubtotal + salesTax);

        return new OrderTotals(
                b2(merchandiseTotal),
                b2(legacyRound2(fees)),
                b2(discountTotal),
                b2(taxableSubtotal),
                taxRate,
                b2(salesTax),
                b2(total),
                b2(discountTotal),
                taxExempt);
    }

    /** The POS's roundCurrency: {@code Math.round((v + Number.EPSILON) * 100) / 100}. */
    public static double legacyRound2(double v) {
        return Math.round((v + Math.ulp(1.0)) * 100) / 100.0;
    }

    private static double d(BigDecimal value) {
        return Double.parseDouble(value.toPlainString());
    }

    private static BigDecimal b2(double value) {
        // Plain setScale with no rounding mode: a legacy-rounded double must
        // already be a whole number of cents; an exception here exposes a bug.
        return BigDecimal.valueOf(value).setScale(2);
    }

    private OrderTotals calculateHalfUp(List<PricingLine> lines,
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
