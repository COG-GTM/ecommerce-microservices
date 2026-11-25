package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.VatBand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VAT summary per rate band for a priced basket (UK, VAT-inclusive prices).
 * <p>
 * Rounding rule: VAT is computed once per rate band from the band's gross, never per line:
 * {@code vat = round_half_up(gross * rate / (100 + rate))}, {@code net = gross - vat}.
 * A band's gross is its lines plus adjustments tied to those lines; basket-level adjustments (no lineId) are
 * apportioned across bands pro rata by gross (largest remainder), so the band grosses always sum to totalPence.
 */
public final class VatAnalysis {

    private VatAnalysis() {
    }

    public static List<VatBand> analyse(Basket basket) {
        Map<VatRate, Long> gross = new EnumMap<>(VatRate.class);
        Map<String, VatRate> rateByLine = new HashMap<>();
        for (BasketLine line : basket.getLines()) {
            VatRate rate = VatRate.of(line.getVatCode());
            rateByLine.put(line.getLineId(), rate);
            gross.merge(rate, (long) line.getLinePence(), Long::sum);
        }
        if (gross.isEmpty()) {
            return List.of();
        }
        for (Adjustment adjustment : basket.getAdjustments()) {
            VatRate rate = adjustment.lineId() == null ? null : rateByLine.get(adjustment.lineId());
            if (rate != null) {
                gross.merge(rate, (long) adjustment.amountPence(), Long::sum);
            }
        }
        apportion(gross, basket.getTotalPence());
        List<VatBand> bands = new ArrayList<>();
        gross.forEach((rate, bandGross) -> {
            int g = Math.toIntExact(bandGross);
            int vat = vatOf(g, rate.percent());
            bands.add(new VatBand(rate.name(), rate.percent(), g - vat, vat, g));
        });
        return List.copyOf(bands);
    }

    /** VAT contained in a VAT-inclusive gross amount, rounded half up to the penny. */
    static int vatOf(int grossPence, int ratePercent) {
        return BigDecimal.valueOf((long) grossPence * ratePercent)
                .divide(BigDecimal.valueOf(100L + ratePercent), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /** Spreads the difference between the bands and the basket total pro rata by band gross. */
    private static void apportion(Map<VatRate, Long> gross, int totalPence) {
        long sum = gross.values().stream().mapToLong(Long::longValue).sum();
        long diff = totalPence - sum;
        if (diff == 0) {
            return;
        }
        if (sum <= 0) {
            gross.replaceAll((rate, g) -> 0L);
            gross.merge(gross.containsKey(VatRate.S) ? VatRate.S : gross.keySet().iterator().next(),
                    (long) totalPence, Long::sum);
            return;
        }
        Map<VatRate, Long> share = new EnumMap<>(VatRate.class);
        Map<VatRate, Long> remainder = new EnumMap<>(VatRate.class);
        long allocated = 0;
        for (Map.Entry<VatRate, Long> e : gross.entrySet()) {
            long scaled = diff * e.getValue();
            long part = Math.floorDiv(scaled, sum);
            share.put(e.getKey(), part);
            remainder.put(e.getKey(), Math.floorMod(scaled, sum));
            allocated += part;
        }
        long left = diff - allocated;
        List<VatRate> byRemainder = new ArrayList<>(gross.keySet());
        byRemainder.sort((a, b) -> Long.compare(remainder.get(b), remainder.get(a)));
        for (int i = 0; i < left; i++) {
            VatRate rate = byRemainder.get(i % byRemainder.size());
            share.merge(rate, 1L, Long::sum);
        }
        share.forEach((rate, part) -> gross.merge(rate, part, Long::sum));
    }
}
