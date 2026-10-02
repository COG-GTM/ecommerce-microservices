package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibatulanand.orderservice.pricing.OrderTotals;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteResponse(
        String storeId,
        List<PricedLineItem> lineItems,
        List<PromotionDto> promotions,
        OrderTotals totals) {
}
