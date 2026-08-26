package com.ibatulanand.inventoryservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class InventoryResponseContractTest {

    @Inject
    InventoryTestData inventoryTestData;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanTable() {
        inventoryTestData.clean();
    }

    @Test
    void responseMatchesCanonicalFixture() throws IOException {
        seed("iphone_15", 100);
        seed("iphone_15_pro", 0);

        String body = given()
                .queryParam("skuCode", "iphone_15")
                .queryParam("skuCode", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode actual = objectMapper.readTree(body);
        JsonNode expected;
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/inventory-response.json")) {
            expected = objectMapper.readTree(fixture);
        }
        assertEquals(expected, actual);

        Set<String> expectedKeys = Set.of("skuCode", "inStock");
        for (JsonNode item : actual) {
            Set<String> actualKeys = new HashSet<>();
            Iterator<String> fieldNames = item.fieldNames();
            fieldNames.forEachRemaining(actualKeys::add);
            assertEquals(expectedKeys, actualKeys);
        }
    }

    void seed(String skuCode, int quantity) {
        inventoryTestData.seed(skuCode, quantity);
    }
}
