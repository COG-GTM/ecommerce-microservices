package com.ibatulanand.inventoryservice;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class InventoryControllerTest {

    @Inject
    InventoryTestData inventoryTestData;

    @BeforeEach
    void cleanTable() {
        inventoryTestData.clean();
    }

    @Test
    void returnsOnlyKnownSkusWithStableJsonKeys() {
        seed("iphone_15", 100);
        seed("iphone_15_pro", 0);

        String body = given()
                .queryParam("skuCode", "iphone_15")
                .queryParam("skuCode", "iphone_15_pro")
                .queryParam("skuCode", "unknown_sku")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertTrue(body.equals("[{\"skuCode\":\"iphone_15\",\"inStock\":true},{\"skuCode\":\"iphone_15_pro\",\"inStock\":false}]"));
        assertFalse(body.contains("isInStock"));
        given()
                .queryParam("skuCode", "iphone_15")
                .queryParam("skuCode", "iphone_15_pro")
                .queryParam("skuCode", "unknown_sku")
                .when()
                .get("/api/inventory")
                .then()
                .body("size()", equalTo(2))
                .body("skuCode", hasItem("iphone_15"))
                .body("skuCode", hasItem("iphone_15_pro"));
    }

    @Test
    void supportsRepeatedAndCommaJoinedQueryParameters() {
        seed("iphone_15", 100);
        seed("iphone_15_pro", 0);

        given()
                .queryParam("skuCode", "iphone_15")
                .queryParam("skuCode", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("skuCode", hasItem("iphone_15"))
                .body("skuCode", hasItem("iphone_15_pro"));

        given()
                .queryParam("skuCode", "iphone_15,iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("skuCode", hasItem("iphone_15"))
                .body("skuCode", hasItem("iphone_15_pro"));
    }

    @Test
    void handlesUnknownBlankAndMissingParameters() {
        given()
                .queryParam("skuCode", "unknown_sku")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body(equalTo("[]"));

        given()
                .queryParam("skuCode", "")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body(equalTo("[]"));

        given()
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(400);
    }

    @Test
    void exposesHealthAndMetricsEndpoints() {
        String healthBody = given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .extract()
                .asString()
                .toLowerCase();
        assertTrue(healthBody.contains("datasource")
                || healthBody.contains("agroal")
                || healthBody.contains("database connections health check"));

        given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("# HELP"));
    }

    void seed(String skuCode, int quantity) {
        inventoryTestData.seed(skuCode, quantity);
    }
}
