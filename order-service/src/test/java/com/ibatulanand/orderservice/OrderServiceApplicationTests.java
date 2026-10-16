package com.ibatulanand.orderservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.HttpUrl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "notificationTopic")
class OrderServiceApplicationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MockWebServer STUBS = new MockWebServer();
    private static final Map<String, String> PRODUCTS = new HashMap<>();
    private static final Map<String, String> INVENTORY = new HashMap<>();
    private static volatile boolean inventoryDown;
    private static Consumer<String, String> consumer;

    static {
        try {
            for (JsonNode p : MAPPER.readTree(fixture("products.json"))) {
                PRODUCTS.put(p.get("skuCode").asText(), MAPPER.writeValueAsString(p));
            }
            MAPPER.readTree(fixture("inventory.json")).fields().forEachRemaining(e ->
                    INVENTORY.put(e.getKey() + "@" + e.getValue().get("storeId").asText(), e.getValue().toString()));
            STUBS.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    HttpUrl url = request.getRequestUrl();
                    String sku = url.queryParameter("skuCode");
                    String body = null;
                    if ("/api/product".equals(url.encodedPath())) {
                        body = PRODUCTS.get(sku);
                    } else if ("/api/inventory".equals(url.encodedPath())) {
                        if (inventoryDown) {
                            return new MockResponse().setResponseCode(503);
                        }
                        body = INVENTORY.get(sku + "@" + url.queryParameter("storeId"));
                    }
                    return body == null
                            ? new MockResponse().setResponseCode(404)
                            : new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
                }
            });
            STUBS.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void stubs(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + STUBS.getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.inventory-service[0].uri", () -> uri);
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    @Autowired
    private TransactionTemplate tx;
    @SpyBean
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        inventoryDown = false;
        reset(orderRepository);
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        if (consumer == null) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("order-service-test", "false", broker);
            props.put("auto.offset.reset", "earliest");
            consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                    .createConsumer();
            broker.consumeFromAnEmbeddedTopic(consumer, "notificationTopic");
        }
        drain(Duration.ofMillis(300));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (consumer != null) {
            consumer.close();
        }
        STUBS.shutdown();
    }

    @Test
    void goldenCartReturns201WithPre1TotalsAndPublishesOneV2Event() throws Exception {
        ResponseEntity<String> response = post(goldenCart("117.65", false, null));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        JsonNode totals = body.get("totals");
        assertMoney(totals, "merchandiseTotal", "159.85");
        assertMoney(totals, "servicesAndFees", "12.00");
        assertMoney(totals, "discountTotal", "63.54");
        assertMoney(totals, "taxableSubtotal", "108.31");
        assertMoney(totals, "salesTax", "9.34");
        assertMoney(totals, "total", "117.65");
        assertThat(totals.get("taxRate").decimalValue()).isEqualByComparingTo("0.08625");
        assertThat(totals.get("taxExempt").asBoolean()).isFalse();

        BigDecimal allocated = BigDecimal.ZERO;
        for (JsonNode line : body.get("lineItems")) {
            allocated = allocated.add(line.get("promotionAmount").decimalValue());
            if ("268341-016-L".equals(line.get("skuCode").asText())) {
                assertThat(line.get("promotionAmount").decimalValue()).isEqualByComparingTo("0.00");
            }
        }
        assertThat(allocated).isEqualByComparingTo(body.get("promotions").get(0).get("amount").decimalValue());

        String orderNumber = body.get("orderNumber").asText();
        List<ConsumerRecord<String, String>> events = drain(Duration.ofSeconds(5));
        assertThat(events).hasSize(1);
        ConsumerRecord<String, String> event = events.get(0);
        assertThat(event.key()).isEqualTo(orderNumber);
        assertThat(new String(event.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                .isEqualTo("event");
        JsonNode payload = MAPPER.readTree(event.value());
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder("schemaVersion", "orderNumber", "storeId",
                "registerId", "associateId", "placedAt", "lineItems", "promotions", "services", "tenders",
                "totals", "taxExempt", "receiptDelivery");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(payload.get("orderNumber").asText()).isEqualTo(orderNumber);
        assertThat(payload.get("storeId").asText()).isEqualTo("1969");
        assertThat(payload.get("registerId").asText()).isEqualTo("04");
        assertThat(payload.get("associateId").asText()).isEqualTo("A-4471");
        assertThat(payload.get("lineItems")).hasSize(3);
        assertThat(fieldNames(payload.get("lineItems").get(0))).containsExactlyInAnyOrder("skuCode", "description",
                "colorName", "size", "quantity", "unitPrice", "extendedPrice", "finalSale", "fulfillmentType",
                "fulfillmentStoreId");
        assertThat(payload.get("promotions").get(0).asText()).isEqualTo("FALL30");
        assertThat(fieldNames(payload.get("tenders").get(0))).containsExactlyInAnyOrder("type", "amount");
        assertThat(payload.get("tenders").get(0).get("type").asText()).isEqualTo("CREDIT_DEBIT");
        assertMoney(payload.get("totals"), "total", "117.65");
        assertThat(payload.get("receiptDelivery").get("channel").asText()).isEqualTo("EMAIL");
        assertThat(orderRepository.findByOrderNumber(orderNumber)).isPresent();
    }

    @Test
    void taxExemptWithCertificateTotals10831() throws Exception {
        ResponseEntity<String> response = post(goldenCart("108.31", true, "NY-ST-119-0042"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode totals = MAPPER.readTree(response.getBody()).get("totals");
        assertMoney(totals, "salesTax", "0.00");
        assertMoney(totals, "total", "108.31");
        assertThat(totals.get("taxExempt").asBoolean()).isTrue();
    }

    @Test
    void taxExemptWithoutCertificateIs422() throws Exception {
        assertProblem(post(goldenCart("108.31", true, null)), 422, "validation-failed");
    }

    @Test
    void unknownSkuIs409OutOfStock() throws Exception {
        String body = goldenCart("117.65", false, null).replace("471902-004-29", "999999-000-X");
        assertProblem(post(body), 409, "out-of-stock");
        assertThat(drain(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void unknownPromotionIs422() throws Exception {
        assertProblem(post(goldenCart("117.65", false, null).replace("FALL30", "BOGUS99")), 422, "invalid-promotion");
    }

    @Test
    void tenderMismatchIs422() throws Exception {
        assertProblem(post(goldenCart("100.00", false, null)), 422, "validation-failed");
    }

    @Test
    void inventoryDownIs503ProblemJsonNot201() throws Exception {
        inventoryDown = true;
        assertProblem(post(goldenCart("117.65", false, null)), 503, "dependency-unavailable");
        assertThat(drain(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void shipFromStoreRequiresEligibility() throws Exception {
        String ineligible = goldenCart("117.65", false, null).replace(
                "{\"skuCode\": \"512884-022-M\", \"quantity\": 1}",
                "{\"skuCode\": \"512884-022-M\", \"quantity\": 1, \"fulfillmentType\": \"SHIP_FROM_STORE\"}");
        assertProblem(post(ineligible), 409, "out-of-stock");

        String eligible = goldenCart("117.65", false, null).replace(
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 1}",
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 1, \"fulfillmentType\": \"SHIP_FROM_STORE\"}");
        ResponseEntity<String> response = post(eligible);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode event = MAPPER.readTree(drain(Duration.ofSeconds(5)).get(0).value());
        assertThat(event.get("lineItems").get(1).get("fulfillmentType").asText()).isEqualTo("SHIP_FROM_STORE");
    }

    @Test
    void stockIsAggregatedPerSkuAndSourceStore() throws Exception {
        String splitShip = goldenCart("117.65", false, null).replace(
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 1}",
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 2, \"fulfillmentType\": \"SHIP_FROM_STORE\", \"fulfillmentStoreId\": \"1177\"},"
                        + "{\"skuCode\": \"471902-004-29\", \"quantity\": 2, \"fulfillmentType\": \"SHIP_FROM_STORE\", \"fulfillmentStoreId\": \"1177\"}");
        assertProblem(post(splitShip), 409, "out-of-stock");

        String sameStoreShip = goldenCart("117.65", false, null).replace(
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 1}",
                "{\"skuCode\": \"471902-004-29\", \"quantity\": 4, \"fulfillmentType\": \"SHIP_FROM_STORE\"},"
                        + "{\"skuCode\": \"471902-004-29\", \"quantity\": 4}");
        assertProblem(post(sameStoreShip), 409, "out-of-stock");
    }

    @Test
    void tamperedClientPriceHasNoEffect() throws Exception {
        String tampered = goldenCart("117.65", false, null)
                .replace("\"quantity\": 1}", "\"quantity\": 1, \"price\": 0.01, \"unitPrice\": 0.01, \"listPrice\": 0.01}");
        ResponseEntity<String> response = post(tampered);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String orderNumber = MAPPER.readTree(response.getBody()).get("orderNumber").asText();
        assertMoney(MAPPER.readTree(response.getBody()).get("totals"), "total", "117.65");

        Map<String, BigDecimal> persisted = tx.execute(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
            Map<String, BigDecimal> prices = new HashMap<>();
            for (OrderLineItems line : order.getOrderLineItemsList()) {
                prices.put(line.getSkuCode(), line.getUnitPrice());
                assertThat(line.getPrice()).isEqualByComparingTo(line.getUnitPrice());
            }
            return prices;
        });
        assertThat(persisted.get("268341-016-L")).isEqualByComparingTo("17.97");
        assertThat(persisted.get("471902-004-29")).isEqualByComparingTo("69.95");
        assertThat(persisted.get("512884-022-M")).isEqualByComparingTo("41.97");
    }

    @Test
    void failedSaveRollsBackAndPublishesNothing() throws Exception {
        doThrow(new IllegalStateException("simulated write failure")).when(orderRepository).save(any(Order.class));
        long before = orderRepository.count();

        ResponseEntity<String> response = post(goldenCart("117.65", false, null));

        assertThat(response.getStatusCode().value()).isNotEqualTo(201);
        assertThat(drain(Duration.ofSeconds(3))).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(before);
    }

    private static String goldenCart(String tender, boolean taxExempt, String certificate) {
        return """
                {
                  "storeId": "1969",
                  "registerId": "04",
                  "associateId": "A-4471",
                  "lineItems": [
                    {"skuCode": "268341-016-L", "quantity": 1},
                    {"skuCode": "471902-004-29", "quantity": 1},
                    {"skuCode": "512884-022-M", "quantity": 1}
                  ],
                  "promotions": ["FALL30"],
                  "services": [{"code": "ALT", "description": "Hemming", "amount": 12.00}],
                  "tenders": [{"type": "CREDIT_DEBIT", "label": "Visa •••• 4242", "amount": %s}],
                  "taxExempt": %s,
                  %s
                  "receiptDelivery": {"channel": "EMAIL", "destination": "jordan@example.com"}
                }
                """.formatted(tender, taxExempt,
                certificate == null ? "" : "\"taxExemptCertificate\": \"" + certificate + "\",");
    }

    private ResponseEntity<String> post(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/order", new HttpEntity<>(json, headers), String.class);
    }

    private static void assertProblem(ResponseEntity<String> response, int status, String slug) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = MAPPER.readTree(response.getBody());
        assertThat(problem.get("type").asText()).endsWith(":" + slug);
        assertThat(problem.get("status").asInt()).isEqualTo(status);
    }

    private static void assertMoney(JsonNode node, String field, String expected) {
        assertThat(node.get(field).decimalValue()).as(field).isEqualByComparingTo(expected);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<ConsumerRecord<String, String>> drain(Duration window) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + window.toMillis();
        while (System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            if (!records.isEmpty() && window.toMillis() > 1000) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                break;
            }
        }
        consumer.commitSync();
        return records;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = OrderServiceApplicationTests.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
