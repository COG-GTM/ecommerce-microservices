package com.ibatulanand.orderservice;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

@QuarkusTest
@TestProfile(TimeoutTestProfile.class)
@QuarkusTestResource(WireMockInventoryResource.class)
class TimeoutContractTest {

    private static final String FALLBACK =
            "Oops! Something went wrong, please order after some time!";

    @BeforeEach
    void resetWireMock() {
        WireMockInventoryResource.server().resetAll();
    }

    @Test
    void timeoutUsesFallbackBeforeStubDelayExpires() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(aResponse().withFixedDelay(30000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"skuCode\":\"slow\",\"inStock\":true}]")));

        long started = System.nanoTime();
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"slow","price":1,"quantity":1}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201)
                .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                .body(org.hamcrest.Matchers.equalTo(FALLBACK));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMillis, lessThan(15000L));
        assertThat(WireMockInventoryResource.server().getAllServeEvents().size(),
                greaterThan(0));
    }
}
