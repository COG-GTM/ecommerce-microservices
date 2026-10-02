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
public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String styleId;
    private String skuCode;
    private String department;
    private String category;
    private String colorName;
    private String colorCode;
    private String size;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private Integer clearancePercent;
    private Boolean finalSale;
}
