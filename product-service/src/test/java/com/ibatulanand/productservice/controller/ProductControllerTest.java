package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProductController.class, properties = "eureka.client.enabled=false")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    void shouldReturnProductsMatchingSkuCode() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .styleId("268341")
                .skuCode("268341-016-L")
                .salePrice(new BigDecimal("17.97"))
                .build();
        when(productService.getProductsBySkuCode("268341-016-L")).thenReturn(List.of(product));

        mockMvc.perform(get("/api/product").param("skuCode", "268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"));

        verify(productService).getProductsBySkuCode("268341-016-L");
        verify(productService, never()).getAllProducts();
    }

    @Test
    void shouldReturnEmptyArrayWhenSkuCodeDoesNotMatch() throws Exception {
        when(productService.getProductsBySkuCode("000000-000-X")).thenReturn(List.of());

        mockMvc.perform(get("/api/product").param("skuCode", "000000-000-X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(productService).getProductsBySkuCode("000000-000-X");
        verify(productService, never()).getAllProducts();
    }

    @Test
    void shouldReturnAllProductsWhenSkuCodeIsNotProvided() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(ProductResponse.builder().name("Product").build()));

        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(productService).getAllProducts();
        verify(productService, never()).getProductsBySkuCode(anyString());
    }
}
