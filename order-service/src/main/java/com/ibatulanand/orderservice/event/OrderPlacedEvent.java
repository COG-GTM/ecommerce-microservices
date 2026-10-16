package com.ibatulanand.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published to {@code notificationTopic} (type token {@code event}, key = {@code orderNumber})
 * after the order commits. v1 was {@code {orderNumber}}; v2 adds everything needed for a receipt
 * or a fulfillment request. Changes must stay additive.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {
    public static final int SCHEMA_VERSION = 2;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;
    private String orderNumber;
    private String storeId;
    private String registerId;
    private String associateId;
    /** ISO-8601 instant, e.g. {@code 2026-10-16T21:30:00Z}. */
    private String placedAt;
    private List<LineItem> lineItems;
    private List<String> promotions;
    private List<Service> services;
    private List<Tender> tenders;
    private Totals totals;
    private boolean taxExempt;
    private ReceiptDelivery receiptDelivery;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LineItem {
        private String skuCode;
        private String description;
        private String colorName;
        private String size;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal extendedPrice;
        private boolean finalSale;
        /** {@code TAKE_WITH} or {@code SHIP_FROM_STORE}. */
        private String fulfillmentType;
        private String fulfillmentStoreId;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Service {
        private String code;
        private String description;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Tender {
        /** {@code CREDIT_DEBIT}, {@code GIFT_CARD} or {@code MOBILE_WALLET}. */
        private String type;
        private BigDecimal amount;
    }

    /** Same fields as {@code types.ts#OrderTotals}. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Totals {
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

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReceiptDelivery {
        /** {@code EMAIL}, {@code SMS}, {@code PRINT} or {@code NONE}. */
        private String channel;
        private String destination;
    }
}
