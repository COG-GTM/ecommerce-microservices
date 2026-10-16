package com.ibatulanand.productservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code pos-webapp/src/api/types.ts#Product}, flattened to one variant.
 * The legacy {@code id} and {@code price} fields are only populated on the list call.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "styleId", "skuCode", "name", "description", "department", "category",
        "colorName", "colorCode", "size", "listPrice", "salePrice", "clearancePercent", "finalSale",
        "price", "variants"})
public class ProductResponse {
    private String id;
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
    private Integer clearancePercent;
    private Boolean finalSale;
    private BigDecimal price;
    @Builder.Default
    private List<ProductVariantResponse> variants = new ArrayList<>();
}
