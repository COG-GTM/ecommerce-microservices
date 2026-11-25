package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.OrderLineItems;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@lombok.Builder
@NoArgsConstructor
public class OrderRequest {
    private List<OrderLineItemsDto> orderLineItemsDtoList;
    private String channel;
    private String storeNumber;
    private String tillId;
    private String transactionId;
    private String tenderType;
    private Integer totalPence;
    private List<OrderTenderDto> tenders;
}
