package com.ibatulanand.notificationservice;

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
    /** SALE or REFUND (absent on events from before refunds existed). */
    private String eventType;
    private String originalOrderNumber;
}
