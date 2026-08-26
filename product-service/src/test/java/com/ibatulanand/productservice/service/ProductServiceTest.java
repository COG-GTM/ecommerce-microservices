package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.TestHelper;
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
    void should_save_product_from_request() {
        ProductRequest productRequest = TestHelper.productRequestFixture("iphone");

        productService.createProduct(productRequest);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertEquals(productRequest.getName(), saved.getName());
        assertEquals(productRequest.getDescription(), saved.getDescription());
        assertEquals(productRequest.getPrice(), saved.getPrice());
    }

    @Test
    void should_map_products_to_responses() {
        Product product = TestHelper.productFixture("iphone");
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
    void should_return_empty_list_when_no_products() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertTrue(productService.getAllProducts().isEmpty());
    }
}
