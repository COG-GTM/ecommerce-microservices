package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the customer actually paid for each line of a priced basket, so a later refund pays back that and not the shelf
 * price. An adjustment comes off the lines it was earned on ({@link Adjustment#lineIds()}), shared in proportion to
 * their value and never taking a line below zero, so a meal-deal saving stays on the meal-deal items. Adjustments tied
 * to no line are shared across every line the same way. The result always adds up to the basket total.
 */
public final class LinePayments {

    private LinePayments() {
    }

    /** @return paid pence per lineId, in basket line order */
    public static Map<String, Integer> allocate(Basket basket) {
        Map<String, Long> paid = new LinkedHashMap<>();
        for (BasketLine line : basket.getLines()) {
            paid.merge(line.getLineId(), (long) line.getLinePence(), Long::sum);
        }
        List<Adjustment> adjustments = basket.getAdjustments() == null ? List.of() : basket.getAdjustments();
        for (Adjustment adjustment : adjustments) {
            List<String> lineIds = adjustment.lineIds().stream().filter(paid::containsKey).distinct().toList();
            if (!lineIds.isEmpty()) {
                spread(paid, lineIds, adjustment.amountPence());
            }
        }
        long delta = basket.getTotalPence() - paid.values().stream().mapToLong(Long::longValue).sum();
        if (delta != 0 && !paid.isEmpty()) {
            spread(paid, List.copyOf(paid.keySet()), delta);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        paid.forEach((lineId, pence) -> result.put(lineId, Math.toIntExact(pence)));
        return result;
    }

    /**
     * Shares amount across lineIds in proportion to their current value (evenly when they are all zero), never taking
     * a line below zero; leftover pennies go one at a time to the first lines that can take them.
     */
    static void spread(Map<String, Long> paid, List<String> lineIds, long amount) {
        Map<String, Long> weights = new LinkedHashMap<>();
        lineIds.forEach(id -> weights.put(id, paid.get(id)));
        long weightTotal = weights.values().stream().mapToLong(Long::longValue).sum();
        long placed = 0;
        for (String id : lineIds) {
            long share = weightTotal == 0 ? amount / lineIds.size() : amount * weights.get(id) / weightTotal;
            share = Math.max(share, -paid.get(id));
            paid.put(id, paid.get(id) + share);
            placed += share;
        }
        long step = Long.signum(amount - placed);
        boolean progress = true;
        while (placed != amount && progress) {
            progress = false;
            for (String id : lineIds) {
                if (placed == amount) {
                    break;
                }
                if (paid.get(id) + step >= 0) {
                    paid.put(id, paid.get(id) + step);
                    placed += step;
                    progress = true;
                }
            }
        }
    }
}
