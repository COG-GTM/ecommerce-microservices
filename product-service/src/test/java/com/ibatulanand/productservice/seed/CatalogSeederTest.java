package com.ibatulanand.productservice.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSeederTest {

    @Mock
    private ProductRepository productRepository;

    private CatalogSeeder catalogSeeder;
    private Map<String, Product> store;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        catalogSeeder = new CatalogSeeder(productRepository, new ObjectMapper());
    }

    private void mockRepository() {
        when(productRepository.findBySkuCode(anyString()))
                .thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(i -> {
                    Product p = i.getArgument(0);
                    store.put(p.getSkuCode(), p);
                    return p;
                });
    }

    @Test
    void seedInsertsAllThirtySkusAndIsIdempotent() throws IOException {
        mockRepository();

        catalogSeeder.seed();
        assertEquals(30, store.size());

        // second run upserts in place instead of duplicating
        catalogSeeder.seed();
        assertEquals(30, store.size());
    }

    @Test
    void seedDataHasUniqueSkuCodesAndPriceEqualsSalePrice() throws IOException {
        mockRepository();

        catalogSeeder.seed();

        assertEquals(30, store.keySet().stream().distinct().count());
        store.values().forEach(p -> {
            assertNotNull(p.getSalePrice(), p.getSkuCode() + " missing salePrice");
            assertEquals(0, p.getSalePrice().compareTo(p.getPrice()),
                    p.getSkuCode() + " price != salePrice");
        });
    }
}
