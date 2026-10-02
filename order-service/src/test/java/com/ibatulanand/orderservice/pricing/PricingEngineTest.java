package com.ibatulanand.orderservice.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {

    private static final BigDecimal FEES = new BigDecimal("12.00");
    private static final BigDecimal RATE = new BigDecimal("0.08625");
    private static final PromotionRule FALL30 =
            new PromotionRule("FALL30", "30% off", new BigDecimal("30"), BigDecimal.ZERO);
    private static final PromotionRule CARD10 =
            new PromotionRule("CARD10", "10% off", new BigDecimal("10"), BigDecimal.ZERO);
    private static final PromotionRule FIVE_OFF =
            new PromotionRule("FIVE", "5 off", BigDecimal.ZERO, new BigDecimal("5.00"));

    // These cases assert exact-decimal HALF_UP tie behaviour.
    private final PricingEngine engine = new PricingEngine(PricingRoundingMode.HALF_UP);
    private final PricingEngine legacyEngine = new PricingEngine(PricingRoundingMode.LEGACY_POS);

    private static PricingLine line(String list, String sale, int qty, boolean finalSale) {
        return new PricingLine(new BigDecimal(list), new BigDecimal(sale), qty, finalSale);
    }

    private static List<PricingLine> mockupLines() {
        return List.of(
                line("29.95", "17.97", 1, true),
                line("69.95", "69.95", 1, false),
                line("59.95", "41.97", 1, false));
    }

    private static void assertTotals(OrderTotals t, String merch, String fees, String discount,
                                     String taxable, String rate, String tax, String total,
                                     String saved, boolean exempt) {
        assertEquals(0, t.merchandiseTotal().compareTo(new BigDecimal(merch)), "merchandiseTotal");
        assertEquals(0, t.servicesAndFees().compareTo(new BigDecimal(fees)), "servicesAndFees");
        assertEquals(0, t.discountTotal().compareTo(new BigDecimal(discount)), "discountTotal");
        assertEquals(0, t.taxableSubtotal().compareTo(new BigDecimal(taxable)), "taxableSubtotal");
        assertEquals(0, t.taxRate().compareTo(new BigDecimal(rate)), "taxRate");
        assertEquals(0, t.salesTax().compareTo(new BigDecimal(tax)), "salesTax");
        assertEquals(0, t.total().compareTo(new BigDecimal(total)), "total");
        assertEquals(0, t.savedToday().compareTo(new BigDecimal(saved)), "savedToday");
        assertEquals(exempt, t.taxExempt(), "taxExempt");
    }

    @Test
    void mockupCartWithFall30() {
        OrderTotals t = engine.calculate(mockupLines(), List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "159.85", "12.00", "63.54", "108.31", "0.08625", "9.34", "117.65", "63.54", false);
    }

    @Test
    void stackedPromotions() {
        OrderTotals t = engine.calculate(mockupLines(), List.of(FALL30, CARD10), FEES, RATE, false);
        assertTotals(t, "159.85", "12.00", "74.73", "97.12", "0.08625", "8.38", "105.50", "74.73", false);
    }

    @Test
    void taxExempt() {
        OrderTotals t = engine.calculate(mockupLines(), List.of(FALL30), FEES, RATE, true);
        assertTotals(t, "159.85", "12.00", "63.54", "108.31", "0.08625", "0.00", "108.31", "63.54", true);
    }

    @Test
    void allFinalSalePromoNotApplied() {
        List<PricingLine> lines = List.of(
                line("29.95", "17.97", 2, true),
                line("19.95", "9.97", 1, true),
                line("44.95", "17.98", 1, true));
        OrderTotals t = engine.calculate(lines, List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "124.80", "12.00", "60.91", "75.89", "0.08625", "6.55", "82.44", "60.91", false);
    }

    @Test
    void halfCentDiscountRoundsUp() {
        // 9 x 29.95 @ 30%: promotion total is exactly 80.865, a half cent.
        // The POS double is 80.864999999999995 and rounds DOWN to 80.86 (total 218.00);
        // BigDecimal HALF_UP rounds UP to 80.87 (total 217.99). Documented divergence.
        List<PricingLine> lines = List.of(line("29.95", "29.95", 9, false));
        OrderTotals t = engine.calculate(lines, List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "269.55", "12.00", "80.87", "200.68", "0.08625", "17.31", "217.99", "80.87", false);
    }

    @Test
    void halfCentTaxRoundsUp() {
        // taxableSubtotal 180.00 x 0.08625 = 15.525 exactly; HALF_UP gives 15.53
        // (the POS double is 15.524999999999999 and gives 15.52). Documented divergence.
        List<PricingLine> lines = List.of(
                line("29.95", "17.97", 1, true),
                line("59.95", "41.97", 3, false),
                line("39.95", "29.97", 2, false),
                line("19.95", "9.97", 2, true));
        OrderTotals t = engine.calculate(lines, List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "329.60", "12.00", "161.60", "180.00", "0.08625", "15.53", "195.53", "161.60", false);
    }

    @Test
    void amountOffPromotion() {
        List<PricingLine> lines = List.of(line("69.95", "69.95", 1, false));
        OrderTotals t = engine.calculate(lines, List.of(FIVE_OFF), FEES, RATE, false);
        assertTotals(t, "69.95", "12.00", "5.00", "76.95", "0.08625", "6.64", "83.59", "5.00", false);
    }

    @Test
    void noPromotionsDiscountIsMarkdownOnly() {
        OrderTotals t = engine.calculate(mockupLines(), List.of(), FEES, RATE, false);
        // markdown = (29.95-17.97) + (69.95-69.95) + (59.95-41.97) = 29.96
        assertTotals(t, "159.85", "12.00", "29.96", "141.89", "0.08625", "12.24", "154.13", "29.96", false);
    }

    /**
     * Guard: Java Math.round and legacyRound2 must match JS Math.round and the
     * POS's Math.round((v + Number.EPSILON) * 100) / 100, captured with:
     *   node -e 'for (const x of [2.5,-2.5,-0.5,-1.5,0.49999999999999994,-0.49999999999999994,1.005,2.675,80.865,15.525,0.125,-0.125,0.0]) console.log(x, Math.round(x), Math.round((x+Number.EPSILON)*100)/100)'
     * Java Math.round returns long, so JS -0 compares equal to 0 below.
     */
    @Test
    void legacyRoundMatchesJsMathRound() {
        // {input, Math.round(input), legacyRound2(input)} — verbatim Node output.
        double[][] table = {
                {2.5, 3, 2.5},
                {-2.5, -2, -2.5},
                {-0.5, 0, -0.5},          // JS returns -0 here; numerically equal to 0
                {-1.5, -1, -1.5},
                {0.49999999999999994, 0, 0.5},
                {-0.49999999999999994, 0, -0.5},  // JS returns -0
                {1.005, 1, 1.01},
                {2.675, 3, 2.68},
                {80.865, 81, 80.86},
                {15.525, 16, 15.53},
                {0.125, 0, 0.13},
                {-0.125, 0, -0.12},       // JS returns -0
                {0.0, 0, 0.0},
        };
        for (double[] row : table) {
            double input = row[0];
            assertEquals((long) row[1], Math.round(input), "Math.round(" + input + ")");
            assertEquals(row[2], PricingEngine.legacyRound2(input), "legacyRound2(" + input + ")");
        }
    }

    @Test
    void legacyPosHalfCentDiscountRoundsDown() {
        // Cart 44 scenario: 9 x 29.95 @ 30% -> 80.865 stored as 80.864999999999995,
        // the POS rounds down to 80.86 while HALF_UP gives 80.87.
        List<PricingLine> lines = List.of(line("29.95", "29.95", 9, false));
        OrderTotals t = legacyEngine.calculate(lines, List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "269.55", "12.00", "80.86", "200.69", "0.08625", "17.31", "218.00", "80.86", false);
    }

    @Test
    void legacyPosHalfCentTaxRoundsDown() {
        // Cart 49 scenario: taxableSubtotal 180.00 x 0.08625 computes to
        // 15.524999999999999 in double -> POS 15.52, HALF_UP 15.53.
        List<PricingLine> lines = List.of(
                line("29.95", "17.97", 1, true),
                line("59.95", "41.97", 3, false),
                line("39.95", "29.97", 2, false),
                line("19.95", "9.97", 2, true));
        OrderTotals t = legacyEngine.calculate(lines, List.of(FALL30), FEES, RATE, false);
        assertTotals(t, "329.60", "12.00", "161.60", "180.00", "0.08625", "15.52", "195.52", "161.60", false);
    }
}
