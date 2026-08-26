package com.ibatulanand.orderservice;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;

import java.math.BigDecimal;
import java.util.List;

public class TestHelper {

    public static OrderLineItemsDto orderLineItemsDtoFixture(String skuCode) {
        return new OrderLineItemsDto(null, skuCode, BigDecimal.valueOf(1500), 1);
    }

    public static OrderRequest orderRequestFixture(String... skuCodes) {
        return new OrderRequest(List.of(skuCodes).stream()
                .map(TestHelper::orderLineItemsDtoFixture)
                .toList());
    }

    public static OrderLineItems orderLineItemsFixture(String skuCode) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setSkuCode(skuCode);
        orderLineItems.setPrice(BigDecimal.valueOf(1500));
        orderLineItems.setQuantity(1);
        return orderLineItems;
    }

    public static Order orderFixture(String orderNumber, String... skuCodes) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setOrderLineItemsList(
                List.of(skuCodes).stream().map(TestHelper::orderLineItemsFixture).toList());
        return order;
    }

    public static InventoryResponse inventoryResponseFixture(String skuCode, boolean inStock) {
        return InventoryResponse.builder().skuCode(skuCode).isInStock(inStock).build();
    }
}
