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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductPersistsAllRequestFields() {
        ProductRequest request = new ProductRequest("iphone_15", "iPhone 15", "Apple iPhone 15", new BigDecimal("999.99"));

        productService.createProduct(request);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getSkuCode()).isEqualTo("iphone_15");
        assertThat(saved.getValue().getName()).isEqualTo("iPhone 15");
        assertThat(saved.getValue().getDescription()).isEqualTo("Apple iPhone 15");
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("999.99");
    }

    @Test
    void getAllProductsMapsEveryEntityToResponse() {
        when(productRepository.findAll()).thenReturn(List.of(
                new Product("1", "iphone_15", "iPhone 15", "Apple iPhone 15", new BigDecimal("999.99")),
                new Product("2", "iphone_15_pro", "iPhone 15 Pro", "Apple iPhone 15 Pro", new BigDecimal("1199.00"))));

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses).containsExactly(
                new ProductResponse("1", "iphone_15", "iPhone 15", "Apple iPhone 15", new BigDecimal("999.99")),
                new ProductResponse("2", "iphone_15_pro", "iPhone 15 Pro", "Apple iPhone 15 Pro", new BigDecimal("1199.00")));
    }

    @Test
    void getAllProductsReturnsEmptyListWhenCatalogIsEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productService.getAllProducts()).isEmpty();
    }
}
