package com.ibatulanand.inventoryservice;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(InventoryLegacySchemaTestProfile.class)
@QuarkusTestResource(InventoryLegacySchemaResource.class)
class InventoryLegacySchemaTest {

    @Inject
    InventoryTestData inventoryTestData;

    @Test
    void servesRequestsAgainstTheLegacySchema() {
        inventoryTestData.clean();
        seed("iphone_15", 100);
        seed("iphone_15_pro", 0);

        given()
                .queryParam("skuCode", "iphone_15")
                .queryParam("skuCode", "iphone_15_pro")
                .when()
                .get("/api/inventory")
                .then()
                .statusCode(200)
                .body(equalTo("[{\"skuCode\":\"iphone_15\",\"inStock\":true},{\"skuCode\":\"iphone_15_pro\",\"inStock\":false}]"));
    }

    private void seed(String skuCode, int quantity) {
        inventoryTestData.seed(skuCode, quantity);
    }
}
