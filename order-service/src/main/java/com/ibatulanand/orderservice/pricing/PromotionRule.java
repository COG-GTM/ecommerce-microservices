package com.ibatulanand.orderservice.pricing;

import java.math.BigDecimal;

public record PromotionRule(String code, String description, BigDecimal percentOff, BigDecimal amountOff) {
}
