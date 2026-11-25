package com.ibatulanand.orderservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsDto {
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String skuCode;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Min(1)
    @Max(10000)
    private Integer quantity;

    /** Drops trailing zeros beyond cents (e.g. 1200.000 -> 1200.00) without expanding huge exponents. */
    public void setPrice(BigDecimal price) {
        this.price = price;
        if (price != null && price.scale() > 2) {
            BigDecimal stripped = price.stripTrailingZeros();
            if (stripped.scale() <= 2 && stripped.scale() >= -12) {
                this.price = stripped.setScale(2);
            }
        }
    }
}
