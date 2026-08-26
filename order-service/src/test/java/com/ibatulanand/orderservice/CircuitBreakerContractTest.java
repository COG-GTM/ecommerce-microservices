package com.ibatulanand.orderservice;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(CircuitBreakerTestProfile.class)
@QuarkusTestResource(WireMockInventoryResource.class)
class CircuitBreakerContractTest {

    private static final String FALLBACK =
            "Oops! Something went wrong, please order after some time!";

    @BeforeEach
    void resetWireMock() {
        WireMockInventoryResource.server().resetAll();
    }

    @Test
    void repeatedInventoryFailuresOpenCircuitAndUseFallback() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(serverError()));

        for (int i = 0; i < 5; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"orderLineItemsDtoList":[{"id":null,"skuCode":"broken","price":1,"quantity":1}]}
                            """.trim())
                    .when().post("/api/order")
                    .then().statusCode(201)
                    .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                    .body(org.hamcrest.Matchers.equalTo(FALLBACK));
        }

        int requestsAfterTrip = WireMockInventoryResource.server().getAllServeEvents().size();
        assertThat(requestsAfterTrip, greaterThan(0));

        for (int i = 0; i < 3; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"orderLineItemsDtoList":[{"id":null,"skuCode":"broken","price":1,"quantity":1}]}
                            """.trim())
                    .when().post("/api/order")
                    .then().statusCode(201)
                    .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                    .body(org.hamcrest.Matchers.equalTo(FALLBACK));
        }

        assertEquals(requestsAfterTrip,
                WireMockInventoryResource.server().getAllServeEvents().size());
    }
}
