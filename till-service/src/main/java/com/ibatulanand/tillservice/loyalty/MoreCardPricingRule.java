package com.ibatulanand.tillservice.loyalty;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.pricing.PricingRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** More Card member prices: a saving per line when a member is attached and the member price beats the shelf price. */
@Component
public class MoreCardPricingRule implements PricingRule {

    public static final String CODE = "MORE_CARD";

    private final MoreCardProperties properties;

    public MoreCardPricingRule(MoreCardProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Adjustment> apply(Basket basket) {
        List<Adjustment> adjustments = new ArrayList<>();
        if (basket.getMember() == null) {
            return adjustments;
        }
        for (BasketLine line : basket.getLines()) {
            Integer memberPrice = properties.memberPrices().get(line.getBarcode());
            if (memberPrice != null && memberPrice < line.getUnitPricePence()) {
                int saving = Math.multiplyExact(line.getUnitPricePence() - memberPrice, line.getQuantity());
                adjustments.add(new Adjustment(CODE, "More Card price: " + line.getName(), -saving, line.getLineId()));
            }
        }
        return adjustments;
    }
}
