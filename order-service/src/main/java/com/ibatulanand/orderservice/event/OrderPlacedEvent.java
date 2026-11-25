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
    /** SALE or REFUND. */
    private String eventType;
    private String originalOrderNumber;

    public OrderPlacedEvent(String orderNumber, String channel, String storeNumber, String tillId,
                            String transactionId, String tenderType, Integer totalPence) {
        this(orderNumber, channel, storeNumber, tillId, transactionId, tenderType, totalPence, "SALE", null);
    }

    public OrderPlacedEvent(String orderNumber) {
        this.orderNumber = orderNumber;
    }
}
