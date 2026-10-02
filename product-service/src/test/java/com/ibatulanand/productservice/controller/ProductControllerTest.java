package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.dto.ProductVariantDto;
import com.ibatulanand.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@TestPropertySource(properties = {"eureka.client.enabled=false"})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return ProductResponse.builder()
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
                .variants(List.of(ProductVariantDto.builder()
                        .skuCode("268341-016-L")
                        .colorName("Heather Grey")
                        .colorCode("#9b9ea3")
                        .size("L")
                        .listPrice(new BigDecimal("29.95"))
                        .salePrice(new BigDecimal("17.97"))
                        .build()))
                .build();
    }

    @Test
    void getAllProductsReturnsArray() throws Exception {
        when(productService.getProducts(null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"));
    }

    @Test
    void getProductsWithSkuCodeParamReturnsArray() throws Exception {
        when(productService.getProducts("268341-016-L")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/product").param("skuCode", "268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].styleId").value("268341"));
    }

    @Test
    void getProductBySkuCodeReturnsFieldsAndVariants() throws Exception {
        when(productService.getProductBySkuCode("268341-016-L")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/product/sku/268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleId").value("268341"))
                .andExpect(jsonPath("$.department").value("WOMEN'S"))
                .andExpect(jsonPath("$.colorCode").value("#9b9ea3"))
                .andExpect(jsonPath("$.clearancePercent").value(40))
                .andExpect(jsonPath("$.finalSale").value(true))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].size").value("L"));
    }

    @Test
    void getProductByUnknownSkuCodeReturns404() throws Exception {
        when(productService.getProductBySkuCode("nope"))
                .thenThrow(new com.ibatulanand.productservice.exception.ProductNotFoundException("nope"));

        mockMvc.perform(get("/api/product/sku/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProductReturns201() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/product")
                        .contentType("application/json")
                        .content("{\"skuCode\":\"268341-016-L\",\"name\":\"Vintage Soft Crewneck Tee\",\"salePrice\":17.97}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skuCode").value("268341-016-L"));
    }

    @Test
    void createDuplicateSkuCodeReturns409() throws Exception {
        when(productService.createProduct(any(ProductRequest.class)))
                .thenThrow(new DuplicateKeyException("duplicate skuCode"));

        mockMvc.perform(post("/api/product")
                        .contentType("application/json")
                        .content("{\"skuCode\":\"268341-016-L\"}"))
                .andExpect(status().isConflict());
    }
}
