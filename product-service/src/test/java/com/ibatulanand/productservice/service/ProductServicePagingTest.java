package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServicePagingTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
        when(productRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            List<Product> products = IntStream.range(0, pageable.getPageSize())
                    .mapToObj(i -> new Product("id" + i, "name" + i, "desc", BigDecimal.ONE))
                    .toList();
            return new PageImpl<>(products, pageable, 10_000);
        });
    }

    @Test
    void neverReturnsMoreThanMaxPageSize() {
        List<ProductResponse> products = productService.getAllProducts(0, 10_000);

        assertThat(products).hasSize(ProductService.MAX_PAGE_SIZE);
        assertThat(requestedPage().getPageSize()).isEqualTo(ProductService.MAX_PAGE_SIZE);
    }

    @Test
    void neverCallsUnpagedFindAll() {
        productService.getAllProducts(0, ProductService.DEFAULT_PAGE_SIZE);

        verify(productRepository, never()).findAll();
        verify(productRepository, never()).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void passesRequestedPageAndSortsById() {
        productService.getAllProducts(3, 25);

        Pageable pageable = requestedPage();
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void raisesNonPositiveSizeToOne() {
        assertThat(productService.getAllProducts(0, 0)).hasSize(1);
        assertThat(productService.getAllProducts(0, -5)).hasSize(1);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> productService.getAllProducts(-1, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    private Pageable requestedPage() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
