package com.ibatulanand.productservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Document(value = "product")
@CompoundIndex(
        name = Product.SKU_CODE_INDEX,
        def = "{'variants.skuCode': 1}",
        unique = true,
        partialFilter = "{'variants.skuCode': {$exists: true}}")
@CompoundIndex(
        name = Product.STYLE_ID_INDEX,
        def = "{'styleId': 1}",
        unique = true,
        partialFilter = "{'styleId': {$exists: true}}")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Product {
    public static final String SKU_CODE_INDEX = "variants_skuCode_unique";
    public static final String STYLE_ID_INDEX = "styleId_unique";

    @Id
    private String id;
    private String styleId;
    private String name;
    private String description;
    private String department;
    private String category;
    /** Legacy field, kept equal to the default variant's listPrice. */
    private BigDecimal price;
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();
}
