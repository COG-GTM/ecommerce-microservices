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

    private static final int MAX_NORMALIZED_PRECISION = 30;

    /**
     * Rewrites prices to cents when that is exact (1200.000 -> 1200.00, 0e10 -> 0.00).
     * Long or huge-exponent values are left as-is for validation to reject.
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
        if (price != null && (price.scale() > 2 || price.scale() < 0)
                && price.precision() <= MAX_NORMALIZED_PRECISION) {
            BigDecimal stripped = price.stripTrailingZeros();
            if (stripped.scale() <= 2 && stripped.scale() >= -12) {
                this.price = stripped.setScale(2);
            }
        }
    }
}
