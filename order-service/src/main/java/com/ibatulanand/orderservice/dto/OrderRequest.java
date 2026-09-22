package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    private List<OrderLineItemsDto> orderLineItemsDtoList;

    private String storeId;
    private String registerId;
    private String associateId;
    private String lane;

    private BigDecimal servicesAndFees;
    private BigDecimal taxRate;
    private boolean taxExempt;

    private List<PromotionDto> promotions;
    private List<TenderDto> tenders;
}
