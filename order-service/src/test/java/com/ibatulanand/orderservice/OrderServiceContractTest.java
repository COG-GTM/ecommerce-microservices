package com.ibatulanand.orderservice;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.List;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(WireMockInventoryResource.class)
@QuarkusTestResource(io.quarkus.test.kafka.KafkaCompanionResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceContractTest {

    private static final String SUCCESS = "Order Placed Successfully!";
    private static final String FALLBACK =
            "Oops! Something went wrong, please order after some time!";

    @Inject
    TestDatabaseCleaner databaseCleaner;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @Inject
    InMemorySpanExporterBean spanExporter;

    @InjectKafkaCompanion
    KafkaCompanion kafka;

    @BeforeEach
    void resetWireMock() {
        WireMockInventoryResource.server().resetAll();
        databaseCleaner.clear();
    }

    @Test
    @Order(1)
    void happyPathPreservesHttpBodyAndPersistsJoinRow() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .withQueryParam("skuCode", equalTo("iphone_15"))
                .willReturn(okJson("[{\"skuCode\":\"iphone_15\",\"inStock\":true}]")));

        String body = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"iphone_15","price":1200,"quantity":1}]}
                        """.trim())
                .when()
                .post("/api/order")
                .then()
                .statusCode(201)
                .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                .extract().asString();

        assertEquals(SUCCESS, body);
        assertThat(count("t_orders_order_line_items_list"), greaterThan(0L));
        assertThat(count("t_orders"), greaterThan(0L));
        assertThat(count("t_order_line_items"), greaterThan(0L));
    }

    private long count(String table) {
        return ((Number) entityManager.createNativeQuery("select count(*) from " + table)
                .getSingleResult()).longValue();
    }

    @Test
    @Order(2)
    void repeatedSkuQueryParametersRemainOrdered() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(okJson("""
                        [{"skuCode":"iphone_15","inStock":true},{"skuCode":"pixel_9","inStock":true}]
                        """)));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"iphone_15","price":1200,"quantity":1},{"id":null,"skuCode":"pixel_9","price":900,"quantity":2}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201).body(org.hamcrest.Matchers.equalTo(SUCCESS));

        List<ServeEvent> events = WireMockInventoryResource.server().getAllServeEvents();
        assertEquals(1, events.size());
        assertEquals("/api/inventory?skuCode=iphone_15&skuCode=pixel_9",
                events.get(0).getRequest().getUrl());
    }

    @Test
    @Order(3)
    void absentInventoryResponseIsSuccessful() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(okJson("[]")));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"unknown_sku","price":1,"quantity":1}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201)
                .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                .body(org.hamcrest.Matchers.equalTo(SUCCESS));
    }

    @Test
    @Order(4)
    void outOfStockUsesFallbackResponse() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(okJson("[{\"skuCode\":\"x\",\"inStock\":false}]")));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"x","price":1,"quantity":1}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201)
                .header("Content-Type", org.hamcrest.Matchers.equalTo("text/plain;charset=UTF-8"))
                .body(org.hamcrest.Matchers.equalTo(FALLBACK));
    }

    @Test
    @Order(5)
    void kafkaRecordHasRawPayloadAndTypeHeader() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(okJson("[{\"skuCode\":\"iphone_15\",\"inStock\":true}]")));
        ConsumerTask<String, byte[]> records = kafka
                .<String, byte[]>consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .withOffsetReset(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST)
                .fromTopics("notificationTopic");
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"iphone_15","price":1200,"quantity":1}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201);

        org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record =
                records.awaitNextRecord(Duration.ofSeconds(15)).getLastRecord();
        assertNull(record.key());
        assertArrayEquals("event".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                record.headers().lastHeader("__TypeId__").value());
        assertThat(new String(record.value(), java.nio.charset.StandardCharsets.UTF_8),
                matchesPattern("\\{\"orderNumber\":\"[0-9a-f-]+\"\\}"));
    }

    @Test
    @Order(6)
    void healthAndMetricsAreExposed() {
        given().when().get("/q/health").then().statusCode(200)
                .body(org.hamcrest.Matchers.containsString("\"status\": \"UP\""));
        given().when().get("/q/metrics").then().statusCode(200)
                .header("Content-Type", org.hamcrest.Matchers.startsWith("application/openmetrics-text"));
    }

    @Test
    @Order(7)
    void inventoryClientSpanIsChildOfIncomingHttpSpan() {
        spanExporter.reset();
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(okJson("[{\"skuCode\":\"trace_sku\",\"inStock\":true}]")));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"orderLineItemsDtoList":[{"id":null,"skuCode":"trace_sku","price":1,"quantity":1}]}
                        """.trim())
                .when().post("/api/order")
                .then().statusCode(201);

        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> spanExporter.finishedSpanItems().size() >= 2);
        List<SpanData> spans = spanExporter.finishedSpanItems();
        SpanData server = spans.stream()
                .filter(span -> span.getName().contains("POST") && span.getName().contains("/api/order"))
                .findFirst().orElseThrow();
        SpanData inventory = spans.stream()
                .filter(span -> span.getName().contains("inventory"))
                .findFirst().orElseThrow();
        assertEquals(server.getSpanContext().getTraceId(), inventory.getSpanContext().getTraceId());
        assertEquals(server.getSpanContext().getSpanId(), inventory.getParentSpanContext().getSpanId());
    }

    @Test
    @Order(8)
    void repeatedInventoryFailuresOpenCircuitAndUseFallback() {
        WireMockInventoryResource.server().stubFor(get(urlPathEqualTo("/api/inventory"))
                .willReturn(serverError()));

        for (int i = 0; i < 6; i++) {
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

        assertThat(WireMockInventoryResource.server().getAllServeEvents().size(),
                org.hamcrest.Matchers.lessThan(18));
    }
}
