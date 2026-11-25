package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsDto {
    private Long id;
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
    private Integer minimumAge;
    private String ageApprovedBy;
    /** What the customer actually paid for the whole line after savings (More Card, promotions), in pence. */
    private Integer paidPence;
    private String name;

    public OrderLineItemsDto(Long id, String skuCode, BigDecimal price, Integer quantity) {
        this(id, skuCode, price, quantity, null, null, null, null);
    }
}
