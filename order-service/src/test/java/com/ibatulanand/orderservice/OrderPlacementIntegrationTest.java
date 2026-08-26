package com.ibatulanand.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.support.TestWebClients;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end order placement against a real MySQL and Kafka (Testcontainers). The inventory service
 * is stubbed with a {@link MockWebServer} so the test needs no other infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderPlacementIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final MockWebServer INVENTORY_SERVICE = new MockWebServer();
    private static final AtomicReference<MockResponse> INVENTORY_RESPONSE = new AtomicReference<>();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @TestConfiguration
    static class StubbedInventoryConfig {

        @Bean
        @Primary
        WebClient.Builder inventoryStubWebClientBuilder() {
            return TestWebClients.redirectingTo(INVENTORY_SERVICE);
        }
    }

    @BeforeAll
    static void startInventoryStub() throws Exception {
        // A dispatcher (instead of a fixed queue) answers every attempt, so the test stays valid
        // regardless of how many retries the circuit breaker configuration performs.
        INVENTORY_SERVICE.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return INVENTORY_RESPONSE.get();
            }
        });
        INVENTORY_SERVICE.start();
    }

    @AfterAll
    static void stopInventoryStub() throws Exception {
        INVENTORY_SERVICE.shutdown();
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    @Test
    void persistsOrderAndProducesOrderPlacedEventWhenProductsAreInStock() throws Exception {
        stubInventoryResponse(new InventoryResponse("iphone_13", true));

        try (Consumer<String, String> consumer = notificationTopicConsumer()) {
            ResponseEntity<String> response =
                    restTemplate.postForEntity("/api/order", orderRequest(), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo("Order Placed Successfully!");

            Order order = transactionTemplate.execute(status -> {
                List<Order> orders = orderRepository.findAll();
                assertThat(orders).hasSize(1);
                Order persisted = orders.get(0);
                assertThat(persisted.getId()).isNotNull();
                assertThat(persisted.getOrderNumber()).isNotBlank();
                assertThat(persisted.getOrderLineItemsList())
                        .extracting(OrderLineItems::getSkuCode, OrderLineItems::getQuantity)
                        .containsExactly(Tuple.tuple("iphone_13", 3));
                assertThat(persisted.getOrderLineItemsList().get(0).getPrice())
                        .isEqualByComparingTo(new BigDecimal("1200"));
                return persisted;
            });

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "notificationTopic", Duration.ofSeconds(20));
            assertThat(record.value()).contains(order.getOrderNumber());
        }
    }

    @Test
    void rejectsOrderAndPersistsNothingWhenProductIsOutOfStock() throws Exception {
        stubInventoryResponse(new InventoryResponse("iphone_13", false));

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/order", orderRequest(), String.class);

        // The controller's circuit breaker converts the IllegalArgumentException into the fallback
        assertThat(response.getBody()).isEqualTo("Oops! Something went wrong, please order after some time!");
        assertThat(orderRepository.findAll()).isEmpty();
    }

    private Consumer<String, String> notificationTopicConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        consumer.subscribe(List.of("notificationTopic"));
        consumer.poll(Duration.ofSeconds(1));
        return consumer;
    }

    private static void stubInventoryResponse(InventoryResponse... responses) throws Exception {
        INVENTORY_RESPONSE.set(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(OBJECT_MAPPER.writeValueAsString(responses)));
    }

    private static OrderRequest orderRequest() {
        return new OrderRequest(List.of(
                new OrderLineItemsDto(null, "iphone_13", new BigDecimal("1200"), 3)));
    }
}
