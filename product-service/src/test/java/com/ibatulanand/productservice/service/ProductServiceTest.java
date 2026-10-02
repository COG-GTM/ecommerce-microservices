package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.exception.ProductNotFoundException;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id("id1")
                .styleId("268341")
                .skuCode("268341-016-L")
                .name("Vintage Soft Crewneck Tee")
                .description("Vintage Soft Crewneck Tee")
                .department("WOMEN'S")
                .category("TOPS")
                .colorName("Heather Grey")
                .colorCode("#9b9ea3")
                .size("L")
                .listPrice(new BigDecimal("29.95"))
                .salePrice(new BigDecimal("17.97"))
                .clearancePercent(40)
                .finalSale(true)
                .price(new BigDecimal("17.97"))
                .build();
    }

    @Test
    void getProductBySkuCodeMapsAllFields() {
        when(productRepository.findBySkuCode("268341-016-L")).thenReturn(Optional.of(product));
        when(productRepository.findByStyleId("268341")).thenReturn(List.of(product));

        ProductResponse response = productService.getProductBySkuCode("268341-016-L");

        assertEquals("id1", response.getId());
        assertEquals("268341", response.getStyleId());
        assertEquals("268341-016-L", response.getSkuCode());
        assertEquals("Vintage Soft Crewneck Tee", response.getName());
        assertEquals("WOMEN'S", response.getDepartment());
        assertEquals("TOPS", response.getCategory());
        assertEquals("Heather Grey", response.getColorName());
        assertEquals("#9b9ea3", response.getColorCode());
        assertEquals("L", response.getSize());
        assertEquals(0, new BigDecimal("29.95").compareTo(response.getListPrice()));
        assertEquals(0, new BigDecimal("17.97").compareTo(response.getSalePrice()));
        assertEquals(0, response.getSalePrice().compareTo(response.getPrice()));
        assertEquals(40, response.getClearancePercent());
        assertTrue(response.getFinalSale());
    }

    @Test
    void variantsIncludeEverySkuOfTheStyle() {
        Product sibling = Product.builder()
                .id("id2")
                .styleId("268341")
                .skuCode("268341-016-M")
                .colorName("Heather Grey")
                .colorCode("#9b9ea3")
                .size("M")
                .listPrice(new BigDecimal("29.95"))
                .salePrice(new BigDecimal("17.97"))
                .build();
        when(productRepository.findBySkuCode("268341-016-L")).thenReturn(Optional.of(product));
        when(productRepository.findByStyleId("268341")).thenReturn(List.of(product, sibling));

        ProductResponse response = productService.getProductBySkuCode("268341-016-L");

        assertEquals(2, response.getVariants().size());
        assertEquals("268341-016-L", response.getVariants().get(0).getSkuCode());
        assertEquals("268341-016-M", response.getVariants().get(1).getSkuCode());
        assertEquals("M", response.getVariants().get(1).getSize());
    }

    @Test
    void getProductsWithNullReturnsAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findByStyleIdIn(anyCollection())).thenReturn(List.of(product));

        List<ProductResponse> responses = productService.getProducts(null);

        assertEquals(1, responses.size());
        assertEquals(1, responses.get(0).getVariants().size());
    }

    @Test
    void getProductsWithSkuReturnsOneElementList() {
        when(productRepository.findBySkuCode("X")).thenReturn(Optional.of(product));
        when(productRepository.findByStyleIdIn(anyCollection())).thenReturn(List.of(product));

        List<ProductResponse> responses = productService.getProducts("X");

        assertEquals(1, responses.size());
        assertEquals("268341-016-L", responses.get(0).getSkuCode());
    }

    @Test
    void getProductsWithUnknownSkuReturnsEmptyList() {
        when(productRepository.findBySkuCode("X")).thenReturn(Optional.empty());

        assertTrue(productService.getProducts("X").isEmpty());
    }

    @Test
    void getProductBySkuCodeThrowsWhenNotFound() {
        when(productRepository.findBySkuCode("nope")).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getProductBySkuCode("nope"));
    }

    @Test
    void createProductWithFullRequestMapsAllFields() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findByStyleId("268341")).thenReturn(List.of(product));

        ProductRequest request = ProductRequest.builder()
                .styleId("268341")
                .skuCode("268341-016-XL")
                .name("Vintage Soft Crewneck Tee")
                .description("Vintage Soft Crewneck Tee")
                .department("WOMEN'S")
                .category("TOPS")
                .colorName("Heather Grey")
                .colorCode("#9b9ea3")
                .size("XL")
                .listPrice(new BigDecimal("29.95"))
                .salePrice(new BigDecimal("17.97"))
                .clearancePercent(40)
                .finalSale(true)
                .build();

        ProductResponse response = productService.createProduct(request);

        assertEquals("268341-016-XL", response.getSkuCode());
        assertEquals(0, new BigDecimal("29.95").compareTo(response.getListPrice()));
        assertEquals(0, new BigDecimal("17.97").compareTo(response.getSalePrice()));
        assertEquals(response.getSalePrice(), response.getPrice());
        assertEquals(40, response.getClearancePercent());
        assertTrue(response.getFinalSale());
    }

    @Test
    void createProductWithLegacyPriceOnlySetsSaleAndListPrice() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductRequest request = ProductRequest.builder()
                .name("Iphone 15")
                .description("Apple Iphone 15")
                .price(new BigDecimal("1500"))
                .build();

        ProductResponse response = productService.createProduct(request);

        assertEquals(0, new BigDecimal("1500").compareTo(response.getSalePrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(response.getListPrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(response.getPrice()));
        assertEquals(0, response.getClearancePercent());
        assertFalse(response.getFinalSale());
        assertTrue(response.getVariants().isEmpty());
    }
}
