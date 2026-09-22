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
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private List<OrderLineItemsDto> orderLineItemsDtoList;

    private String storeId;
    private String registerId;
    private String associateId;
    private String lane;

    private BigDecimal merchandiseTotal;
    private BigDecimal servicesAndFees;
    private BigDecimal discountTotal;
    private BigDecimal taxableSubtotal;
    private BigDecimal taxRate;
    private BigDecimal salesTax;
    private BigDecimal total;
    private BigDecimal savedToday;
    private boolean taxExempt;

    private List<PromotionDto> promotions;
    private List<TenderDto> tenders;
}
