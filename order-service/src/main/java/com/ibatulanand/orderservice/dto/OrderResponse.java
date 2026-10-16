package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors {@code types.ts#OrderResponse} ({@code orderNumber, status, message}) plus {@code totals}
 * ({@code types.ts#OrderTotals}). {@code lineItems} and {@code promotions} are additive fields that
 * expose the server-side price breakdown and per-line promotion allocation.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private String orderNumber;
    private String status;
    private String message;
    private OrderTotals totals;
    private List<Line> lineItems;
    private List<AppliedPromotion> promotions;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        private String skuCode;
        private String description;
        private Integer quantity;
        private BigDecimal listPrice;
        private BigDecimal unitPrice;
        private BigDecimal markdownAmount;
        private BigDecimal extendedPrice;
        private boolean finalSale;
        private BigDecimal promotionAmount;
        private List<AppliedPromotion> promotions;
        private String fulfillmentType;
        private String fulfillmentStoreId;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AppliedPromotion {
        private String code;
        private BigDecimal amount;
    }
}
