package com.ibatulanand.orderservice.dto;

import java.util.List;

public class OrderRequest {
    private List<OrderLineItemsDto> orderLineItemsDtoList;

    public OrderRequest() {
    }

    public OrderRequest(List<OrderLineItemsDto> orderLineItemsDtoList) {
        this.orderLineItemsDtoList = orderLineItemsDtoList;
    }

    public List<OrderLineItemsDto> getOrderLineItemsDtoList() {
        return orderLineItemsDtoList;
    }

    public void setOrderLineItemsDtoList(List<OrderLineItemsDto> orderLineItemsDtoList) {
        this.orderLineItemsDtoList = orderLineItemsDtoList;
    }
}
