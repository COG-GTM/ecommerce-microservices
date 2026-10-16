package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.config.ProductSeedData;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.model.ProductVariant;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.service.ProductService.DuplicateSkuException;
import com.ibatulanand.productservice.service.ProductService.FieldError;
import com.ibatulanand.productservice.service.ProductService.ProductValidationException;
import com.ibatulanand.productservice.service.ProductService.SkuNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "268341-016-L, 268341-016-L",
            "268341016L, 268341-016-L",
            "268341016l, 268341-016-L",
            "' 268341 016 L ', 268341-016-L",
            "47190200429, 471902-004-29",
            "471902-00429, 471902-004-29",
            "abc, ABC"
    })
    void normalizesHangtags(String raw, String expected) {
        assertEquals(expected, ProductService.normalizeSkuCode(raw));
    }

    @Test
    void lookupFlattensScannedVariantAndListsTheOthers() {
        Product style = Product.builder().id("x").styleId("600100").name("Tee").description("Tee")
                .department("MEN'S").category("TOPS").price(new BigDecimal("29.95"))
                .variants(List.of(
                        variant("600100-001-S", "29.95", "29.95", 0, false),
                        variant("600100-002-M", "29.95", "17.97", 40, true)))
                .build();
        when(productRepository.findByVariantsSkuCode("600100-002-M")).thenReturn(Optional.of(style));

        ProductResponse response = productService.getProductBySku("600100002m");

        assertEquals("600100-002-M", response.getSkuCode());
        assertEquals(new BigDecimal("17.97"), response.getSalePrice());
        assertEquals(40, response.getClearancePercent());
        assertEquals(Boolean.TRUE, response.getFinalSale());
        assertNull(response.getId());
        assertNull(response.getPrice());
        assertEquals(1, response.getVariants().size());
        assertEquals("600100-001-S", response.getVariants().get(0).getSkuCode());
    }

    @Test
    void unknownSkuThrowsWithNormalizedCode() {
        when(productRepository.findByVariantsSkuCode(any())).thenReturn(Optional.empty());

        SkuNotFoundException e = assertThrows(SkuNotFoundException.class,
                () -> productService.getProductBySku("999999000x"));
        assertEquals("999999-000-X", e.getSkuCode());
    }

    @Test
    void listIncludesLegacyIdAndPrice() {
        when(productRepository.findAll()).thenReturn(List.of(
                Product.builder().id("legacy").name("Iphone 15").description("Apple Iphone 15")
                        .price(new BigDecimal("1500")).build(),
                ProductSeedData.goldenProducts().get(0)));

        List<ProductResponse> list = productService.getAllProducts();

        assertEquals("legacy", list.get(0).getId());
        assertEquals(new BigDecimal("1500.00"), list.get(0).getPrice());
        assertEquals("268341-016-L", list.get(1).getSkuCode());
        assertEquals(new BigDecimal("29.95"), list.get(1).getPrice());
    }

    @Test
    void createDerivesStyleAndLegacyPriceFromVariants() {
        productService.createProduct(ProductRequest.builder().name("Tee")
                .variants(List.of(requestVariant("600100-001-S", "29.95", "17.97", 40),
                        requestVariant("600100002M", "34.95", "34.95", 0)))
                .build());

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertEquals("600100", saved.getValue().getStyleId());
        assertEquals(new BigDecimal("29.95"), saved.getValue().getPrice());
        assertEquals("600100-002-M", saved.getValue().getVariants().get(1).getSkuCode());
        assertEquals(Boolean.FALSE, saved.getValue().getVariants().get(1).getFinalSale());
    }

    @Test
    void clearanceToleranceAllowsHalfCentRounding() {
        productService.createProduct(ProductRequest.builder().name("Hoodie")
                .variants(List.of(requestVariant("512884-022-M", "59.95", "41.97", 30))).build());
        verify(productRepository).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "600100-001-S, 29.95, 39.95, 0, variants[0].salePrice",
            "600100-001-S, 29.95, 29.95, 101, variants[0].clearancePercent",
            "600100-001-S, 29.95, 29.95, -1, variants[0].clearancePercent",
            "600100-001-S, 29.95, 19.99, 40, variants[0].salePrice",
            "not-a-sku, 29.95, 29.95, 0, variants[0].skuCode"
    })
    void rejectsInvalidVariants(String sku, String list, String sale, int pct, String field) {
        ProductValidationException e = assertThrows(ProductValidationException.class,
                () -> productService.createProduct(ProductRequest.builder().name("Tee")
                        .variants(List.of(requestVariant(sku, list, sale, pct))).build()));

        assertTrue(e.getErrors().stream().map(FieldError::field).anyMatch(field::equals), e.getErrors().toString());
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateSkuWithinRequestAndForeignStyle() {
        ProductValidationException e = assertThrows(ProductValidationException.class,
                () -> productService.createProduct(ProductRequest.builder().styleId("600100").name("Tee")
                        .variants(List.of(requestVariant("600100-001-S", "10.00", "10.00", 0),
                                requestVariant("600100001S", "10.00", "10.00", 0),
                                requestVariant("700100-001-S", "10.00", "10.00", 0)))
                        .build()));

        assertEquals(List.of("variants[1].skuCode", "variants[2].skuCode"),
                e.getErrors().stream().map(FieldError::field).toList());
    }

    @Test
    void duplicateKeyFromMongoBecomesDuplicateSkuException() {
        when(productRepository.save(any())).thenThrow(new DuplicateKeyException("E11000"));

        assertThrows(DuplicateSkuException.class, () -> productService.createProduct(ProductRequest.builder()
                .name("Tee").variants(List.of(requestVariant("268341-016-L", "29.95", "17.97", 40))).build()));
    }

    private static ProductVariant variant(String sku, String list, String sale, int pct, boolean finalSale) {
        return ProductVariant.builder().skuCode(sku).listPrice(new BigDecimal(list)).salePrice(new BigDecimal(sale))
                .clearancePercent(pct).finalSale(finalSale).build();
    }

    private static ProductRequest.Variant requestVariant(String sku, String list, String sale, int pct) {
        return ProductRequest.Variant.builder().skuCode(sku).listPrice(new BigDecimal(list))
                .salePrice(new BigDecimal(sale)).clearancePercent(pct).build();
    }
}
