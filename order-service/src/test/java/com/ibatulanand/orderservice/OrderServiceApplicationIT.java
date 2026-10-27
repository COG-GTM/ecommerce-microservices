package com.ibatulanand.orderservice;

import com.ibatulanand.orderservice.repository.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL + real Kafka (Testcontainers) with an HTTP stub standing in for inventory-service,
 * resolved through Spring Cloud's load-balanced WebClient exactly as in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"})
@Testcontainers
class OrderServiceApplicationIT {

    private static final String SUCCESS = "Order Placed Successfully!";
    private static final String FALLBACK = "Oops! Something went wrong, please order after some time!";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33").withDatabaseName("order_service");
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static final AtomicReference<String> inventoryReply = new AtomicReference<>();
    static final HttpServer inventoryStub = startInventoryStub();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.discovery.client.simple.instances.inventory-service[0].uri",
                () -> "http://localhost:" + inventoryStub.getAddress().getPort());
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        circuitBreakers.circuitBreaker("inventory").reset();
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of("notificationTopic"));
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(200));
        }
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @AfterAll
    static void stopStub() {
        inventoryStub.stop(0);
    }

    @Test
    void inStockOrderIsPersistedToMySqlAndPublishedToKafka() {
        inventoryReply.set("[{\"skuCode\":\"iphone_15\",\"inStock\":true}]");

        ResponseEntity<String> response = placeOrder("iphone_15", 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(SUCCESS);
        assertThat(orderRepository.findAll()).hasSize(1);
        String orderNumber = orderRepository.findAll().get(0).getOrderNumber();
        List<String> events = pollEvents(Duration.ofSeconds(15), 1);
        assertThat(events).singleElement().asString().contains("\"orderNumber\":\"" + orderNumber + "\"");
    }

    @Test
    void outOfStockOrderFallsBackAndLeavesNoOrderRowOrEvent() {
        inventoryReply.set("[{\"skuCode\":\"iphone_15_pro\",\"inStock\":false}]");

        ResponseEntity<String> response = placeOrder("iphone_15_pro", 1);

        assertThat(response.getBody()).isEqualTo(FALLBACK);
        assertThat(orderRepository.count()).isZero();
        assertThat(pollEvents(Duration.ofSeconds(3), 1)).isEmpty();
    }

    @Test
    void inventoryOutageTriggersCircuitBreakerFallbackWithoutSideEffects() {
        inventoryReply.set(null);

        ResponseEntity<String> response = placeOrder("iphone_15", 1);

        assertThat(response.getBody()).isEqualTo(FALLBACK);
        assertThat(orderRepository.count()).isZero();
        assertThat(pollEvents(Duration.ofSeconds(3), 1)).isEmpty();
    }

    private ResponseEntity<String> placeOrder(String sku, int quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"orderLineItemsDtoList\":[{\"skuCode\":\"" + sku + "\",\"price\":999.99,\"quantity\":" + quantity + "}]}";
        return rest.postForEntity("/api/order", new HttpEntity<>(body, headers), String.class);
    }

    private List<String> pollEvents(Duration timeout, int expected) {
        List<String> events = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && events.size() < expected) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records) {
                events.add(record.value());
            }
        }
        return events;
    }

    private static HttpServer startInventoryStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/inventory", exchange -> {
                String reply = inventoryReply.get();
                byte[] body = (reply == null ? "{\"error\":\"down\"}" : reply).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply == null ? 503 : 200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
