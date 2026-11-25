package com.ibatulanand.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {
    public static final int NAME_MAX_LENGTH = 200;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    @NotBlank
    @Size(max = NAME_MAX_LENGTH)
    private String name;

    @NotBlank
    @Size(max = DESCRIPTION_MAX_LENGTH)
    private String description;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;
}
