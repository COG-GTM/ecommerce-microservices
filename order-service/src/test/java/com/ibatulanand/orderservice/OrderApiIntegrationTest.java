package com.ibatulanand.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.support.AbstractIntegrationTest;
import com.ibatulanand.orderservice.support.TestFixtures;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end flow through the real application context: inventory-service is replaced by a
 * MockWebServer and the notification topic is served by a Kafka container.
 */
class OrderApiIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final MockWebServer INVENTORY_SERVICE = new MockWebServer();

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    static {
        try {
            INVENTORY_SERVICE.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the inventory-service stub", e);
        }
        KAFKA.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @AfterAll
    static void shutDownInventoryService() throws IOException {
        INVENTORY_SERVICE.shutdown();
    }

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @Test
    void should_publish_order_placed_event_when_all_items_in_stock() {
        INVENTORY_SERVICE.setDispatcher(inventoryDispatcher(true));

        RestAssuredMockMvc.given()
                .contentType(ContentType.JSON)
                .body(TestFixtures.orderRequest("iphone_13", "iphone_13_red"))
                .when()
                .async().timeout(30, TimeUnit.SECONDS)
                .post("/api/order")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body(equalTo("Order Placed Successfully!"));

        List<String> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = notificationTopicConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> messages.add(record.value()));
                assertThat(messages).isNotEmpty();
            });
        }
        assertThat(messages.get(0)).contains("orderNumber");
    }

    @Test
    void should_return_fallback_message_when_item_out_of_stock() {
        INVENTORY_SERVICE.setDispatcher(inventoryDispatcher(false));

        RestAssuredMockMvc.given()
                .contentType(ContentType.JSON)
                .body(TestFixtures.orderRequest("iphone_13"))
                .when()
                .async().timeout(30, TimeUnit.SECONDS)
                .post("/api/order")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body(equalTo("Oops! Something went wrong, please order after some time!"));
    }

    private KafkaConsumer<String, String> notificationTopicConsumer() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-api-integration-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of("notificationTopic"));
        return consumer;
    }

    private static Dispatcher inventoryDispatcher(boolean inStock) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                List<InventoryResponse> body = List.of(
                        new InventoryResponse("iphone_13", inStock),
                        new InventoryResponse("iphone_13_red", inStock));
                try {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(OBJECT_MAPPER.writeValueAsString(body));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    /** Points the load-balanced WebClient at the inventory-service stub. */
    @TestConfiguration
    static class InventoryStubConfiguration {

        @Bean
        @Primary
        WebClient.Builder inventoryStubWebClientBuilder() {
            return WebClient.builder().filter((request, next) -> {
                URI original = request.url();
                String query = original.getRawQuery() == null ? "" : "?" + original.getRawQuery();
                URI rewritten = URI.create(
                        INVENTORY_SERVICE.url("/").toString().replaceAll("/$", "")
                                + original.getRawPath() + query);
                return next.exchange(ClientRequest.from(request).url(rewritten).build());
            });
        }
    }
}
