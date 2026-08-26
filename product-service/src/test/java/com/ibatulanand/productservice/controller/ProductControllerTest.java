package com.ibatulanand.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.TestHelper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mvc);
    }

    @Test
    void should_create_product_success() throws Exception {
        ProductRequest productRequest = TestHelper.productRequestFixture("iphone");

        RestAssuredMockMvc.given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(productRequest))
                .when()
                .post("/api/product")
                .then()
                .statusCode(201);

        verify(productService).createProduct(any(ProductRequest.class));
    }

    @Test
    void should_get_all_products_success() {
        ProductResponse productResponse = TestHelper.productResponseFixture("iphone");
        when(productService.getAllProducts()).thenReturn(List.of(productResponse));

        RestAssuredMockMvc.when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(productResponse.getId()))
                .body("[0].name", equalTo(productResponse.getName()))
                .body("[0].price", equalTo(productResponse.getPrice().intValue()));
    }

    @Test
    void should_get_empty_list_when_no_products() {
        when(productService.getAllProducts()).thenReturn(List.of());

        RestAssuredMockMvc.when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }
}
