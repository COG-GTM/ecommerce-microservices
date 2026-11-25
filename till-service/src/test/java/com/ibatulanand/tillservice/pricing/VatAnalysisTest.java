package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.VatBand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VatAnalysisTest {

    private final BasketPricer pricer = new BasketPricer(List.of());

    private static BasketLine line(String id, int qty, int unitPence, String vatCode) {
        BasketLine line = new BasketLine(id, id, "SKU-" + id, "Item " + id, qty, unitPence, 0);
        line.setVatCode(vatCode);
        return line;
    }

    private static Basket basket(BasketLine... lines) {
        Basket basket = new Basket();
        basket.getLines().addAll(List.of(lines));
        return basket;
    }

    @Test
    void mixedBasketHasOneBandPerRateAndBandsSumToTotal() {
        Basket basket = pricer.price(basket(line("1001", 1, 155, "Z"), line("4001", 2, 120, "S"),
                line("4002", 1, 135, "S")));

        assertThat(basket.getTotalPence()).isEqualTo(530);
        assertThat(basket.getVatSummary()).containsExactly(
                new VatBand("Z", 0, 155, 0, 155),
                new VatBand("S", 20, 312, 63, 375));
        assertThat(basket.getVatSummary().stream().mapToInt(VatBand::grossPence).sum()).isEqualTo(530);
    }

    @Test
    void vatIsRoundedOncePerBandNotPerLine() {
        // Three 20%-rated lines at 1p: per-line VAT would round each 0.1667p down to 0p (total 0p);
        // the band VAT on 3p gross is 0.5p, rounded half up to 1p.
        Basket basket = pricer.price(basket(line("a", 1, 1, "S"), line("b", 1, 1, "S"), line("c", 1, 1, "S")));

        assertThat(basket.getVatSummary()).containsExactly(new VatBand("S", 20, 2, 1, 3));
    }

    @Test
    void vatOfRoundsHalfUp() {
        assertThat(VatAnalysis.vatOf(120, 20)).isEqualTo(20);
        assertThat(VatAnalysis.vatOf(135, 20)).isEqualTo(23);   // 22.5 -> 23
        assertThat(VatAnalysis.vatOf(160, 20)).isEqualTo(27);   // 26.67 -> 27
        assertThat(VatAnalysis.vatOf(999, 0)).isZero();
    }

    @Test
    void missingOrUnknownVatCodeIsStandardRated() {
        Basket basket = pricer.price(basket(line("x", 1, 600, null), line("y", 1, 600, "Q")));

        assertThat(basket.getVatSummary()).containsExactly(new VatBand("S", 20, 1000, 200, 1200));
    }

    @Test
    void emptyBasketHasNoVatBands() {
        assertThat(pricer.price(new Basket()).getVatSummary()).isEmpty();
    }

    @Test
    void lineLinkedSavingsReduceThatLinesBand() {
        PricingRule multibuy = b -> List.of(new Adjustment("MULTIBUY", "2 for £2", -40, "4001"));
        Basket basket = new BasketPricer(List.of(multibuy)).price(basket(line("1001", 1, 155, "Z"),
                line("4001", 2, 120, "S")));

        assertThat(basket.getTotalPence()).isEqualTo(355);
        assertThat(basket.getVatSummary()).containsExactly(
                new VatBand("Z", 0, 155, 0, 155),
                new VatBand("S", 20, 167, 33, 200));
    }

    @Test
    void basketLevelSavingsAreApportionedProRataSoBandsStillSumToTotal() {
        PricingRule loyalty = b -> List.of(new Adjustment("LOYALTY", "£1 off", -100, null));
        Basket basket = new BasketPricer(List.of(loyalty)).price(basket(line("z", 1, 300, "Z"),
                line("s", 1, 100, "S")));

        assertThat(basket.getTotalPence()).isEqualTo(300);
        assertThat(basket.getVatSummary()).containsExactly(
                new VatBand("Z", 0, 225, 0, 225),
                new VatBand("S", 20, 62, 13, 75));
        assertThat(basket.getVatSummary().stream().mapToInt(VatBand::grossPence).sum())
                .isEqualTo(basket.getTotalPence());
    }

    @Test
    void apportionedRemainderPenniesStillSumToTotal() {
        PricingRule loyalty = b -> List.of(new Adjustment("LOYALTY", "10p off", -10, null));
        Basket basket = new BasketPricer(List.of(loyalty)).price(basket(line("z", 1, 101, "Z"),
                line("s", 1, 102, "S")));

        assertThat(basket.getVatSummary().stream().mapToInt(VatBand::grossPence).sum()).isEqualTo(193);
    }
}
