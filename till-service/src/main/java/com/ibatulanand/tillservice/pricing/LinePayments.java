package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the customer actually paid for each line of a priced basket, so a later refund pays back that and not the shelf
 * price. An adjustment tied to a line (a member price, a promotion anchored on it) comes off that line, never below
 * zero; whatever is left over, and adjustments tied to no line, is shared across lines in proportion to their value.
 * The result always adds up to the basket total.
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
            if (adjustment.lineId() != null && paid.containsKey(adjustment.lineId())) {
                paid.merge(adjustment.lineId(), (long) adjustment.amountPence(), Long::sum);
            }
        }
        paid.replaceAll((lineId, pence) -> Math.max(0, pence));

        long target = basket.getTotalPence();
        long delta = target - paid.values().stream().mapToLong(Long::longValue).sum();
        if (delta != 0 && !paid.isEmpty()) {
            Map<String, Long> weights = new LinkedHashMap<>(paid);
            long weightTotal = weights.values().stream().mapToLong(Long::longValue).sum();
            if (weightTotal == 0) {
                weights.replaceAll((lineId, pence) -> 1L);
                weightTotal = weights.size();
            }
            long assigned = 0;
            for (Map.Entry<String, Long> entry : weights.entrySet()) {
                long share = delta * entry.getValue() / weightTotal;
                paid.merge(entry.getKey(), share, Long::sum);
                assigned += share;
            }
            long step = Long.signum(delta - assigned);
            for (Map.Entry<String, Long> entry : paid.entrySet()) {
                if (assigned == delta) {
                    break;
                }
                if (weights.get(entry.getKey()) > 0 && entry.getValue() + step >= 0) {
                    entry.setValue(entry.getValue() + step);
                    assigned += step;
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        paid.forEach((lineId, pence) -> result.put(lineId, Math.toIntExact(pence)));
        return result;
    }
}
