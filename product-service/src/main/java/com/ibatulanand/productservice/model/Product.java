package com.ibatulanand.productservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(value = "product")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Product {
    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String barcode;
    private String skuCode;
    private Integer pricePence;
    private Boolean quickKey;
    /** UK VAT code: Z (zero-rated, 0%) or S (standard-rated, 20%). */
    private String vatCode;
    /** Sold loose by weight; pricePence is then the price per kg. */
    private Boolean soldByWeight;
    /** Minimum customer age to buy this item (Challenge 25 prompt at the till); null when unrestricted. */
    private Integer minimumAge;
}
