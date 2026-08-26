package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.support.AbstractIntegrationTest;
import com.ibatulanand.productservice.support.TestFixtures;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void clearProducts() {
        productRepository.deleteAll();
    }

    @Test
    void should_create_product_success() throws Exception {
        String productRequestString = objectMapper.writeValueAsString(TestFixtures.productRequest("Iphone 15"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestString))
                .andExpect(status().isCreated());
        Assertions.assertEquals(1, productRepository.findAll().size());
    }

    @Test
    void should_return_created_product_when_listing_after_create() throws Exception {
        ProductRequest request = TestFixtures.productRequest("Iphone 15");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value(request.getName()))
                .andExpect(jsonPath("$[0].description").value(request.getDescription()))
                .andExpect(jsonPath("$[0].price").value(request.getPrice().intValueExact()));
    }
}
