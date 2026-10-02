package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PricedLineItem(
        String skuCode,
        String styleId,
        String description,
        String department,
        String colorName,
        String colorCode,
        String size,
        int quantity,
        BigDecimal listPrice,
        BigDecimal unitPrice,
        BigDecimal extendedPrice,
        String discountReason,
        int clearancePercent,
        boolean finalSale) {
}
