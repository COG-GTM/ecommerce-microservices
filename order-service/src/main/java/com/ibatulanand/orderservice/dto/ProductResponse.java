package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Frozen POS-1 contract: {@code GET /api/product?skuCode=} returns one object matching
 * {@code types.ts#Product}, or 404.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductResponse {
    private String styleId;
    private String skuCode;
    private String name;
    private String description;
    private String department;
    private String category;
    private String colorName;
    private String colorCode;
    private String size;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private BigDecimal clearancePercent;
    private boolean finalSale;
    private List<Variant> variants;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Variant {
        private String skuCode;
        private String colorName;
        private String colorCode;
        private String size;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
    }
}
