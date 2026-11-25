package com.ibatulanand.tillservice.pricing.promotion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Active promotions, bound from till.promotions.* (see application.properties). */
@ConfigurationProperties(prefix = "till.promotions")
public record PromotionProperties(List<MultiBuyConfig> multiBuys, List<MealDealConfig> mealDeals) {

    public PromotionProperties {
        multiBuys = multiBuys == null ? List.of() : List.copyOf(multiBuys);
        mealDeals = mealDeals == null ? List.of() : List.copyOf(mealDeals);
    }

    public record MultiBuyConfig(String id, String label, Set<String> barcodes, Integer quantity, Integer pricePence) {
    }

    public record MealDealConfig(String id, String label, Set<String> mains, Set<String> sides, Set<String> drinks,
                                 Integer pricePence) {
    }

    public List<Promotion> promotions() {
        List<Promotion> promotions = new ArrayList<>();
        multiBuys.forEach(m -> promotions.add(new Promotion.MultiBuy(m.id(), m.label(), m.barcodes(),
                required(m.quantity(), m.id(), "quantity"), required(m.pricePence(), m.id(), "price-pence"))));
        mealDeals.forEach(m -> promotions.add(new Promotion.MealDeal(m.id(), m.label(), m.mains(), m.sides(),
                m.drinks(), required(m.pricePence(), m.id(), "price-pence"))));
        return List.copyOf(promotions);
    }

    private static int required(Integer value, String id, String property) {
        return Objects.requireNonNull(value, () -> "till.promotions offer " + id + " is missing " + property);
    }
}
