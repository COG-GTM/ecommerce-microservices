package com.ibatulanand.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsDto {
    public static final int MAX_SKU_CODE_LENGTH = 50;

    private Long id;
    @NotBlank
    @Size(max = MAX_SKU_CODE_LENGTH)
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
}
