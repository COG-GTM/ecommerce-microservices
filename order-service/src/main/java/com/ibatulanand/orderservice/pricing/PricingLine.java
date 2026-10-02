package com.ibatulanand.orderservice.pricing;

import java.math.BigDecimal;

public record PricingLine(BigDecimal listPrice, BigDecimal unitPrice, int quantity, boolean finalSale) {
}
