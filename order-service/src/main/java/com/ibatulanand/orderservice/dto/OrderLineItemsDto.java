package com.ibatulanand.orderservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A requested order line. Price is intentionally absent: it is resolved
 * server-side from the product catalog.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsDto {
    public static final int MAX_QUANTITY = 1000;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$")
    private String skuCode;

    @NotNull
    @Positive
    @Max(MAX_QUANTITY)
    private Integer quantity;
}
