package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.config.PricingProperties;
import com.ibatulanand.orderservice.exception.OrderValidationException;
import com.ibatulanand.orderservice.pricing.PromotionRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the POS PromotionsBar (trim + uppercase) and
 * StoreCheckout.handleApplyPromotion (dedupe, first occurrence wins).
 */
@Component
@RequiredArgsConstructor
public class PromotionResolver {

    private final PricingProperties pricingProperties;

    public List<PromotionRule> resolve(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, PromotionRule> resolved = new LinkedHashMap<>();
        for (String raw : codes) {
            String code = raw == null ? "" : raw.trim().toUpperCase();
            PricingProperties.Promo promo = pricingProperties.getPromotions().get(code);
            if (promo == null) {
                throw new OrderValidationException("Unknown promotion code: " + code);
            }
            resolved.putIfAbsent(code,
                    new PromotionRule(code, promo.getDescription(), promo.getPercentOff(), promo.getAmountOff()));
        }
        return new ArrayList<>(resolved.values());
    }
}
