package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.FulfillmentType;
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
    private String skuCode;
    private Integer quantity;
    /** Defaults to {@link FulfillmentType#TAKE_WITH}. */
    private FulfillmentType fulfillmentType;
    private String fulfillmentStoreId;
    /** Legacy field. Accepted for compatibility but ignored: prices come from product-service. */
    private BigDecimal price;
}
