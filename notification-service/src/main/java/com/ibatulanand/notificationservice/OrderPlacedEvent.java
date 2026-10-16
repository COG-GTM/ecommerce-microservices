package com.ibatulanand.notificationservice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibatulanand.notificationservice.support.PiiMasker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consumer-side copy of order-service's {@code OrderPlacedEvent} (type token {@code event}).
 * v1 is {@code {orderNumber}} with no {@code schemaVersion}; v2 adds everything needed for a receipt
 * or a fulfillment request. Field names mirror the producer exactly; unknown fields are ignored.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPlacedEvent {
    public static final int SCHEMA_VERSION_V1 = 1;

    private Integer schemaVersion;
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

    public OrderPlacedEvent(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public int effectiveSchemaVersion() {
        return schemaVersion == null ? SCHEMA_VERSION_V1 : schemaVersion;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Service {
        private String code;
        private String description;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReceiptDelivery {
        /** {@code EMAIL}, {@code SMS}, {@code PRINT} or {@code NONE}. */
        private String channel;
        private String destination;

        /** {@code true} for {@code EMAIL} / {@code SMS} with a destination; {@code PRINT} and {@code NONE} send nothing. */
        @JsonIgnore
        public boolean isOutbound() {
            return ("EMAIL".equals(channel) || "SMS".equals(channel)) && destination != null && !destination.isBlank();
        }

        @Override
        public String toString() {
            return "ReceiptDelivery(channel=" + channel + ", destination=" + PiiMasker.mask(destination) + ")";
        }
    }
}
