package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.Order;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(String orderNumber, String channel, String storeNumber, String tillId,
                            String transactionId, String tenderType, Integer totalPence, List<Line> lines) {

    public record Line(String skuCode, BigDecimal price, Integer quantity) {
    }

    public static OrderResponse from(Order order) {
        List<Line> lines = order.getOrderLineItemsList() == null ? List.of()
                : order.getOrderLineItemsList().stream()
                .map(l -> new Line(l.getSkuCode(), l.getPrice(), l.getQuantity()))
                .toList();
        return new OrderResponse(order.getOrderNumber(), order.getChannel(), order.getStoreNumber(),
                order.getTillId(), order.getTransactionId(), order.getTenderType(), order.getTotalPence(), lines);
    }
}
