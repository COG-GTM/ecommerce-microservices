package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.Order;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(String orderNumber, String channel, String storeNumber, String tillId,
                            String transactionId, String tenderType, Integer totalPence, List<Line> lines,
                            List<Tender> tenders, String idempotencyKey,
                            String orderType, String originalOrderNumber) {

    public record Line(String skuCode, BigDecimal price, Integer quantity, Integer minimumAge, String ageApprovedBy) {
    }

    public record Tender(String tenderType, Integer amountPence, Integer tenderedPence, Integer changeDuePence,
                         String authCode) {
    }

    public static OrderResponse from(Order order) {
        List<Line> lines = order.getOrderLineItemsList() == null ? List.of()
                : order.getOrderLineItemsList().stream()
                .map(l -> new Line(l.getSkuCode(), l.getPrice(), l.getQuantity(), l.getMinimumAge(),
                        l.getAgeApprovedBy()))
                .toList();
        List<Tender> tenders = order.getTenders() == null ? List.of()
                : order.getTenders().stream()
                .map(t -> new Tender(t.getTenderType(), t.getAmountPence(), t.getTenderedPence(),
                        t.getChangeDuePence(), t.getAuthCode()))
                .toList();
        return new OrderResponse(order.getOrderNumber(), order.getChannel(), order.getStoreNumber(),
                order.getTillId(), order.getTransactionId(), order.getTenderType(), order.getTotalPence(), lines,
                tenders, order.getIdempotencyKey(),
                order.getOrderType() == null ? "SALE" : order.getOrderType(), order.getOriginalOrderNumber());
    }
}
