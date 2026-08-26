package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.support.AbstractIntegrationTest;
import com.ibatulanand.productservice.support.TestFixtures;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void should_create_product_success() throws Exception {
        String productRequestString = objectMapper.writeValueAsString(TestFixtures.productRequest("Iphone 15"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestString))
                .andExpect(status().isCreated());
        Assertions.assertEquals(1, productRepository.findAll().size());
    }

}
