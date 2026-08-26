package com.ibatulanand.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProductController.class, properties = "eureka.client.enabled=false")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void createProductReturnsCreatedAndDelegatesDeserializedRequest() throws Exception {
        ProductRequest productRequest = ProductRequest.builder()
                .name("Monitor")
                .description("4K monitor")
                .price(BigDecimal.valueOf(299.99))
                .build();

        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProductRequest> requestCaptor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).createProduct(requestCaptor.capture());
        ProductRequest capturedRequest = requestCaptor.getValue();
        assertEquals(productRequest.getName(), capturedRequest.getName());
        assertEquals(productRequest.getDescription(), capturedRequest.getDescription());
        assertEquals(productRequest.getPrice(), capturedRequest.getPrice());
    }

    @Test
    void getAllProductsReturnsProductResponsesAsJson() throws Exception {
        ProductResponse productResponse = ProductResponse.builder()
                .id("product-id")
                .name("Keyboard")
                .description("Mechanical keyboard")
                .price(BigDecimal.valueOf(89.99))
                .build();
        when(productService.getAllProducts()).thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(productResponse.getId()))
                .andExpect(jsonPath("$[0].name").value(productResponse.getName()))
                .andExpect(jsonPath("$[0].description").value(productResponse.getDescription()))
                .andExpect(jsonPath("$[0].price").value(productResponse.getPrice().doubleValue()));
    }
}
