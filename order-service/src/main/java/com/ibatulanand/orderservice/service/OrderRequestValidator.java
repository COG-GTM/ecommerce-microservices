package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

final class OrderRequestValidator {

    static final int MAX_LINE_ITEMS = 50;
    static final int MAX_QUANTITY = 1000;
    private static final Pattern SKU_CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private OrderRequestValidator() {
    }

    static List<OrderLineItemsDto> validate(OrderRequest orderRequest) {
        if (orderRequest == null || orderRequest.getOrderLineItemsDtoList() == null
                || orderRequest.getOrderLineItemsDtoList().isEmpty()) {
            throw new InvalidOrderException("orderLineItemsDtoList must contain at least one item");
        }
        List<OrderLineItemsDto> items = orderRequest.getOrderLineItemsDtoList();
        if (items.size() > MAX_LINE_ITEMS) {
            throw new InvalidOrderException("orderLineItemsDtoList must not contain more than " + MAX_LINE_ITEMS + " items");
        }
        for (OrderLineItemsDto item : items) {
            if (item == null) {
                throw new InvalidOrderException("order line item must not be null");
            }
            if (item.getSkuCode() == null || !SKU_CODE.matcher(item.getSkuCode()).matches()) {
                throw new InvalidOrderException("skuCode must be 1-64 characters of letters, digits, '_' or '-'");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > MAX_QUANTITY) {
                throw new InvalidOrderException("quantity must be between 1 and " + MAX_QUANTITY);
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidOrderException("price must be zero or greater");
            }
        }
        return items;
    }
}
