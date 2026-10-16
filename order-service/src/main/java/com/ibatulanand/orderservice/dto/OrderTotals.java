package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Mirrors {@code types.ts#OrderTotals}. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderTotals {
    private BigDecimal merchandiseTotal;
    private BigDecimal servicesAndFees;
    private BigDecimal discountTotal;
    private BigDecimal taxableSubtotal;
    private BigDecimal taxRate;
    private BigDecimal salesTax;
    private BigDecimal total;
    private BigDecimal savedToday;
    private boolean taxExempt;
}
