package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibatulanand.orderservice.pricing.OrderTotals;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(
        String orderNumber,
        String status,
        String message,
        OrderTotals totals,
        List<TenderDto> tenders,
        BigDecimal changeDue) {
}
