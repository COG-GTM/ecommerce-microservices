package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import com.ibatulanand.inventoryservice.support.TestFixtures;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(InventoryController.class)
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @Test
    void should_bind_all_repeated_sku_code_params_into_the_service_argument() {
        when(inventoryService.isInStock(anyList())).thenReturn(List.of());

        RestAssuredMockMvc.given()
                .queryParam("skuCode", "iphone_15", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(HttpStatus.OK.value());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryService).isInStock(captor.capture());
        assertThat(captor.getValue()).containsExactly("iphone_15", "iphone_15_pro");
    }

    @Test
    void should_return_stock_flags_as_json_when_skus_are_requested() {
        when(inventoryService.isInStock(List.of("iphone_15", "iphone_15_pro")))
                .thenReturn(List.of(
                        TestFixtures.inventoryResponse("iphone_15", true),
                        TestFixtures.inventoryResponse("iphone_15_pro", false)));

        RestAssuredMockMvc.given()
                .queryParam("skuCode", "iphone_15", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("size()", equalTo(2))
                .body("[0].skuCode", equalTo("iphone_15"))
                .body("[0].inStock", equalTo(true))
                .body("[1].skuCode", equalTo("iphone_15_pro"))
                .body("[1].inStock", equalTo(false));
    }

    @Test
    void should_return_bad_request_when_sku_code_param_is_missing() {
        RestAssuredMockMvc.given()
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
