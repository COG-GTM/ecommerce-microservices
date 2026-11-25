package com.ibatulanand.tillservice.pricing;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative totals in integer pence. */
@Component
public class BasketPricer {

    private final List<PricingRule> rules;

    public BasketPricer(List<PricingRule> rules) {
        this.rules = rules;
    }

    public Basket price(Basket basket) {
        int subtotal = 0;
        int items = 0;
        for (BasketLine line : basket.getLines()) {
            line.setLinePence(Math.multiplyExact(line.getQuantity(), line.getUnitPricePence()));
            subtotal = Math.addExact(subtotal, line.getLinePence());
            items += line.getQuantity();
        }
        List<Adjustment> adjustments = new ArrayList<>();
        for (PricingRule rule : rules) {
            adjustments.addAll(rule.apply(basket));
        }
        int savings = adjustments.stream().filter(a -> a.amountPence() < 0).mapToInt(a -> -a.amountPence()).sum();
        int charges = adjustments.stream().filter(a -> a.amountPence() > 0).mapToInt(Adjustment::amountPence).sum();
        basket.setAdjustments(adjustments);
        basket.setItemCount(items);
        basket.setSubtotalPence(subtotal);
        basket.setSavingsPence(savings);
        basket.setTotalPence(Math.max(0, subtotal - savings + charges));
        return basket;
    }
}
