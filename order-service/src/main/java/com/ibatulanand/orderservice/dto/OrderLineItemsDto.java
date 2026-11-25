package com.ibatulanand.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    // URL-unreserved characters only, so 100 SKUs always fit in the inventory query URI without percent-encoding
    @Pattern(regexp = "[A-Za-z0-9._~-]*")
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
}
