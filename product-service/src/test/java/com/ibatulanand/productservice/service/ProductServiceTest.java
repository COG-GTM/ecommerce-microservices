package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductBuildsAndSavesProductFromRequest() {
        ProductRequest productRequest = ProductRequest.builder()
                .name("Laptop")
                .description("Portable computer")
                .price(BigDecimal.valueOf(999.99))
                .build();

        productService.createProduct(productRequest);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(productRequest.getName(), savedProduct.getName());
        assertEquals(productRequest.getDescription(), savedProduct.getDescription());
        assertEquals(productRequest.getPrice(), savedProduct.getPrice());
    }

    @Test
    void getAllProductsMapsProductsToResponses() {
        Product product = Product.builder()
                .id("product-id")
                .name("Phone")
                .description("Mobile phone")
                .price(BigDecimal.valueOf(599.99))
                .build();
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<ProductResponse> responses = productService.getAllProducts();

        assertEquals(1, responses.size());
        ProductResponse response = responses.get(0);
        assertEquals(product.getId(), response.getId());
        assertEquals(product.getName(), response.getName());
        assertEquals(product.getDescription(), response.getDescription());
        assertEquals(product.getPrice(), response.getPrice());
    }

    @Test
    void getAllProductsReturnsEmptyListWhenRepositoryIsEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductResponse> responses = productService.getAllProducts();

        assertTrue(responses.isEmpty());
    }
}
