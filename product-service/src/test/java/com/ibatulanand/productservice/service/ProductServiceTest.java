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
    void createProductSavesMappedProduct() {
        ProductRequest request = ProductRequest.builder()
                .name("Iphone 15")
                .description("Apple Iphone 15")
                .price(BigDecimal.valueOf(1500))
                .build();

        productService.createProduct(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getName()).isEqualTo("Iphone 15");
        assertThat(saved.getDescription()).isEqualTo("Apple Iphone 15");
        assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }

    @Test
    void getAllProductsMapsToProductResponseList() {
        Product p1 = Product.builder().id("1").name("A").description("desc A").price(BigDecimal.ONE).build();
        Product p2 = Product.builder().id("2").name("B").description("desc B").price(BigDecimal.TEN).build();
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        List<ProductResponse> responses = productService.getAllProducts();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).usingRecursiveComparison().isEqualTo(
                ProductResponse.builder().id("1").name("A").description("desc A").price(BigDecimal.ONE).build());
        assertThat(responses.get(1)).usingRecursiveComparison().isEqualTo(
                ProductResponse.builder().id("2").name("B").description("desc B").price(BigDecimal.TEN).build());
    }

    @Test
    void getAllProductsReturnsEmptyListWhenRepositoryEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productService.getAllProducts()).isEmpty();
    }
}
