package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponse {
    private String skuCode;
    private String styleId;
    private String name;
    private String description;
    private String department;
    private String category;
    private String colorName;
    private String colorCode;
    private String size;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private Integer clearancePercent;
    private Boolean finalSale;
    private BigDecimal price;

    public BigDecimal effectiveListPrice() {
        return listPrice != null ? listPrice : price;
    }

    public BigDecimal effectiveSalePrice() {
        return salePrice != null ? salePrice : price;
    }

    public int effectiveClearancePercent() {
        return clearancePercent != null ? clearancePercent : 0;
    }

    public boolean effectiveFinalSale() {
        return finalSale != null && finalSale;
    }
}
