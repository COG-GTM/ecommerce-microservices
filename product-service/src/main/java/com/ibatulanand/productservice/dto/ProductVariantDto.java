package com.ibatulanand.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantDto {
    private String skuCode;
    private String colorName;
    private String colorCode;
    private String size;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private Integer clearancePercent;
    private boolean finalSale;
}
