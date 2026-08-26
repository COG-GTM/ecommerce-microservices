package com.ibatulanand.productservice.support;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;

import java.math.BigDecimal;

/** Central place to build product test data, so a DTO change touches one file. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static ProductRequest productRequest(String name) {
        return ProductRequest.builder()
                .name(name)
                .description("Description of " + name)
                .price(BigDecimal.valueOf(1500))
                .build();
    }

    public static Product product(String name) {
        return Product.builder()
                .name(name)
                .description("Description of " + name)
                .price(BigDecimal.valueOf(1500))
                .build();
    }

    public static Product persistedProduct(String id, String name) {
        Product product = product(name);
        product.setId(id);
        return product;
    }

    public static ProductResponse productResponse(String id, String name) {
        return ProductResponse.builder()
                .id(id)
                .name(name)
                .description("Description of " + name)
                .price(BigDecimal.valueOf(1500))
                .build();
    }
}
