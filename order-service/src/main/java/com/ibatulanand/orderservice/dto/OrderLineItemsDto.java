package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsDto {
    private Long id;
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;

    private String description;
    private String colorName;
    private String size;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal extendedPrice;
    private BigDecimal discountAmount;
    private String discountReason;
}
