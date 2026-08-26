package com.ibatulanand.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.service.ProductService;
import com.ibatulanand.productservice.support.TestFixtures;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
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
    void should_return_201_and_pass_request_to_service_when_creating_product() throws Exception {
        ProductRequest request = TestFixtures.productRequest("Iphone 15");

        RestAssuredMockMvc.given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(request))
                .when()
                .post("/api/product")
                .then()
                .statusCode(201);

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).createProduct(captor.capture());
        ProductRequest received = captor.getValue();
        assertThat(received.getName()).isEqualTo(request.getName());
        assertThat(received.getDescription()).isEqualTo(request.getDescription());
        assertThat(received.getPrice()).isEqualByComparingTo(request.getPrice());
    }

    @Test
    void should_return_200_with_product_array_when_listing_products() {
        ProductResponse response = TestFixtures.productResponse("id-1", "Iphone 15");
        when(productService.getAllProducts()).thenReturn(List.of(response));

        RestAssuredMockMvc.when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(response.getId()))
                .body("[0].name", equalTo(response.getName()))
                .body("[0].description", equalTo(response.getDescription()))
                .body("[0].price", equalTo(response.getPrice().intValueExact()));
    }

    @Test
    void should_return_400_when_request_body_is_malformed() {
        RestAssuredMockMvc.given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"name\": ")
                .when()
                .post("/api/product")
                .then()
                .statusCode(400);
    }
}
