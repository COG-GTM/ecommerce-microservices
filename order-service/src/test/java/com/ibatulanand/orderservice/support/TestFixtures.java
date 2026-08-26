package com.ibatulanand.orderservice.support;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Central place to build order test data, so a DTO change touches one file. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static OrderLineItemsDto orderLineItemsDto(String skuCode, int quantity) {
        OrderLineItemsDto dto = new OrderLineItemsDto();
        dto.setSkuCode(skuCode);
        dto.setQuantity(quantity);
        dto.setPrice(BigDecimal.valueOf(1500));
        return dto;
    }

    public static OrderRequest orderRequest(String... skuCodes) {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setOrderLineItemsDtoList(
                List.of(skuCodes).stream().map(skuCode -> orderLineItemsDto(skuCode, 1)).toList());
        return orderRequest;
    }

    public static OrderLineItems orderLineItems(String skuCode, int quantity) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setSkuCode(skuCode);
        orderLineItems.setQuantity(quantity);
        orderLineItems.setPrice(BigDecimal.valueOf(1500));
        return orderLineItems;
    }

    public static Order order(String... skuCodes) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setOrderLineItemsList(
                List.of(skuCodes).stream().map(skuCode -> orderLineItems(skuCode, 1)).toList());
        return order;
    }
}
