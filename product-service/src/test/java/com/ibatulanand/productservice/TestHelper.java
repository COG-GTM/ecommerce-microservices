package com.ibatulanand.productservice;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;

import java.math.BigDecimal;

public class TestHelper {

    public static ProductRequest productRequestFixture(String seed) {
        return ProductRequest.builder()
                .name("name-" + seed)
                .description("description-" + seed)
                .price(BigDecimal.valueOf(1500))
                .build();
    }

    public static Product productFixture(String seed) {
        return Product.builder()
                .id(seed + "-id")
                .name("name-" + seed)
                .description("description-" + seed)
                .price(BigDecimal.valueOf(1500))
                .build();
    }

    public static ProductResponse productResponseFixture(String seed) {
        Product product = productFixture(seed);
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
