package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.ReceiptChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReceiptDeliveryDto {
    private ReceiptChannel channel;
    private String destination;
}
