package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService)).build();
    }

    @Test
    void postCreatesProductAndReturns201() throws Exception {
        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"iphone_15\",\"name\":\"iPhone 15\",\"description\":\"d\",\"price\":999.99}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProductRequest> request = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).createProduct(request.capture());
        assertThat(request.getValue().getSkuCode()).isEqualTo("iphone_15");
        assertThat(request.getValue().getPrice()).isEqualByComparingTo("999.99");
    }

    @Test
    void getReturnsCatalogAs200Json() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(
                new ProductResponse("1", "iphone_15", "iPhone 15", "Apple iPhone 15", new BigDecimal("999.99"))));

        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].skuCode").value("iphone_15"))
                .andExpect(jsonPath("$[0].price").value(999.99));
    }
}
