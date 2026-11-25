package com.ibatulanand.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String channel;
    private String storeNumber;
    private String tillId;
    private String transactionId;
    private String tenderType;
    private Integer totalPence;

    public OrderPlacedEvent(String orderNumber) {
        this.orderNumber = orderNumber;
    }
}
