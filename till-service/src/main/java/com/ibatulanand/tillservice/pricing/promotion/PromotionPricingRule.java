package com.ibatulanand.tillservice.pricing.promotion;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.pricing.PricingRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-buy ("N for £X") and meal-deal savings. Each saving is one {@link Adjustment} attached to the last basket line
 * that qualified for it, so the till shows it directly under the qualifying items. Repeat applications of the same
 * promotion on the same line are combined into one adjustment ("... x2").
 */
@Component
public class PromotionPricingRule implements PricingRule {

    private final List<Promotion> promotions;
    private final PromotionEngine engine;

    @Autowired
    public PromotionPricingRule(PromotionProperties properties) {
        this(properties.promotions());
    }

    PromotionPricingRule(List<Promotion> promotions) {
        this.promotions = List.copyOf(promotions);
        this.engine = new PromotionEngine(promotions);
    }

    @Override
    public List<Adjustment> apply(Basket basket) {
        List<BasketLine> lines = basket.getLines();
        Map<String, Integer> lastLineIndexByBarcode = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            lastLineIndexByBarcode.put(lines.get(i).getBarcode(), i);
        }
        record Key(int lineIndex, int promotionIndex) {
        }
        Map<Key, int[]> totals = new LinkedHashMap<>();
        for (PromotionEngine.Application application : engine.bestApplications(lines)) {
            int anchor = application.consumed().keySet().stream().mapToInt(lastLineIndexByBarcode::get).max()
                    .orElseThrow();
            int[] total = totals.computeIfAbsent(new Key(anchor, promotions.indexOf(application.promotion())),
                    k -> new int[2]);
            total[0] += application.savingsPence();
            total[1]++;
        }
        List<Adjustment> adjustments = new ArrayList<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Key::lineIndex)
                        .thenComparingInt(Key::promotionIndex)))
                .forEach(e -> {
                    Promotion promotion = promotions.get(e.getKey().promotionIndex());
                    int count = e.getValue()[1];
                    String label = count > 1 ? promotion.label() + " x" + count : promotion.label();
                    adjustments.add(new Adjustment(promotion.code(), label, -e.getValue()[0],
                            lines.get(e.getKey().lineIndex()).getLineId()));
                });
        return adjustments;
    }
}
