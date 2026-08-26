package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.support.AbstractIntegrationTest;
import com.ibatulanand.inventoryservice.support.TestFixtures;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

class InventoryApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void seed() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        inventoryRepository.deleteAll();
        inventoryRepository.saveAll(List.of(
                TestFixtures.inventory("iphone_15", 10),
                TestFixtures.inventory("iphone_15_pro", 0)));
    }

    @Test
    void should_expose_stock_flags_for_seeded_skus() {
        RestAssuredMockMvc.given()
                .queryParam("skuCode", "iphone_15", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("size()", equalTo(2))
                .body("find { it.skuCode == 'iphone_15' }.inStock", equalTo(true))
                .body("find { it.skuCode == 'iphone_15_pro' }.inStock", equalTo(false));
    }

    @Test
    void should_omit_unknown_sku_when_it_has_no_inventory_row() {
        RestAssuredMockMvc.given()
                .queryParam("skuCode", "iphone_15", "unknown_sku")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("size()", equalTo(1))
                .body("[0].skuCode", equalTo("iphone_15"));
    }
}
