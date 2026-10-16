package com.ibatulanand.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {
    private String styleId;
    private String name;
    private String description;
    private String department;
    private String category;
    /** Legacy field. When variants are supplied it is derived from the first variant's listPrice. */
    private BigDecimal price;
    @Builder.Default
    private List<Variant> variants = new ArrayList<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Variant {
        private String skuCode;
        private String colorName;
        private String colorCode;
        private String size;
        private BigDecimal listPrice;
        private BigDecimal salePrice;
        private Integer clearancePercent;
        private Boolean finalSale;
    }
}
