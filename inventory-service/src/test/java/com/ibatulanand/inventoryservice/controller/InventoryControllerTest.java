package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.TestHelper;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mvc);
    }

    @Test
    void should_return_stock_status_for_requested_sku_codes() {
        when(inventoryService.isInStock(eq(List.of("iphone_15", "iphone_15_pro"))))
                .thenReturn(List.of(
                        TestHelper.inventoryResponseFixture("iphone_15", true),
                        TestHelper.inventoryResponseFixture("iphone_15_pro", false)));

        RestAssuredMockMvc.given()
                .queryParam("skuCode", "iphone_15", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].skuCode", equalTo("iphone_15"))
                .body("[0].inStock", equalTo(true))
                .body("[1].inStock", equalTo(false));
    }

    @Test
    void should_fail_when_sku_code_param_is_missing() {
        RestAssuredMockMvc.when()
                .get("/api/inventory")
                .then()
                .statusCode(400);
    }
}
