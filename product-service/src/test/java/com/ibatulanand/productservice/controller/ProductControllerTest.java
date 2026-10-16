package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import com.ibatulanand.productservice.service.ProductService.FieldError;
import com.ibatulanand.productservice.service.ProductService.ProductValidationException;
import com.ibatulanand.productservice.service.ProductService.SkuNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ProductService productService;

    @Test
    void skuCodeParamRoutesToSingleLookup() throws Exception {
        when(productService.getProductBySku("268341016L")).thenReturn(ProductResponse.builder()
                .styleId("268341").skuCode("268341-016-L").listPrice(new BigDecimal("29.95")).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product").param("skuCode", "268341016L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.variants").isArray());
        verify(productService, never()).getAllProducts();
    }

    @Test
    void noParamReturnsList() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(ProductResponse.builder()
                .id("abc").name("Iphone 15").description("Apple Iphone 15").price(new BigDecimal("1500.00"))
                .build()));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abc"))
                .andExpect(jsonPath("$[0].name").value("Iphone 15"))
                .andExpect(jsonPath("$[0].price").value(1500.00));
    }

    @Test
    void unknownSkuRendersProblemJson() throws Exception {
        when(productService.getProductBySku("999999-000-X")).thenThrow(new SkuNotFoundException("999999-000-X"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product").param("skuCode", "999999-000-X")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("sku-not-found")))
                .andExpect(jsonPath("$.title").value("SKU not found"))
                .andExpect(jsonPath("$.skuCode").value("999999-000-X"));
    }

    @Test
    void validationFailureRendersProblemJson422() throws Exception {
        doThrow(new ProductValidationException(List.of(new FieldError("variants[0].salePrice", "too high"))))
                .when(productService).createProduct(any());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"styleId\":\"600200\",\"variants\":[{\"skuCode\":\"600200-001-S\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("validation-failed")))
                .andExpect(jsonPath("$.errors[0].field").value("variants[0].salePrice"));
    }
}
