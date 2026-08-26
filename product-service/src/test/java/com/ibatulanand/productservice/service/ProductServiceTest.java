package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    @Test
    void should_save_product_with_every_request_field_when_creating() {
        ProductRequest request = TestFixtures.productRequest("Iphone 15");

        productService.createProduct(request);

        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getName()).isEqualTo(request.getName());
        assertThat(saved.getDescription()).isEqualTo(request.getDescription());
        assertThat(saved.getPrice()).isEqualByComparingTo(request.getPrice());
    }

    @Test
    void should_map_entities_to_responses_when_products_exist() {
        Product product = TestFixtures.persistedProduct("id-1", "Iphone 15");
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses).hasSize(1);
        ProductResponse response = responses.get(0);
        assertThat(response.getId()).isEqualTo(product.getId());
        assertThat(response.getName()).isEqualTo(product.getName());
        assertThat(response.getDescription()).isEqualTo(product.getDescription());
        assertThat(response.getPrice()).isEqualByComparingTo(product.getPrice());
    }

    @Test
    void should_return_empty_list_when_repository_is_empty() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productService.getAllProducts()).isEmpty();
    }
}
