package com.ibatulanand.orderservice.pricing;

import java.math.BigDecimal;

public record OrderTotals(
        BigDecimal merchandiseTotal,
        BigDecimal servicesAndFees,
        BigDecimal discountTotal,
        BigDecimal taxableSubtotal,
        BigDecimal taxRate,
        BigDecimal salesTax,
        BigDecimal total,
        BigDecimal savedToday,
        boolean taxExempt) {
}
