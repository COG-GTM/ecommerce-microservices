package com.ibatulanand.systemtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.ibatulanand.systemtests.StackClient.ORDER_FALLBACK;
import static com.ibatulanand.systemtests.StackClient.ORDER_PLACED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end system tests over real HTTP against the composed topology:
 * Keycloak -> api-gateway -> (Eureka) -> product-service/MongoDB, order-service/MySQL -> inventory-service/MySQL,
 * order-service -> Kafka -> notification-service. Inventory seeds iphone_15 (qty 100) and iphone_15_pro (qty 0).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFlowSystemIT {

    private static final String TOPIC = "notificationTopic";
    private static final String NOTIFICATION_GROUP = "notificationId";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private static final StackClient stack = new StackClient();
    private static Admin admin;
    private static KafkaConsumer<String, String> eventProbe;

    @BeforeAll
    static void connectToKafka() throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, StackClient.KAFKA_BOOTSTRAP);
        admin = Admin.create(adminProps);
        try {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw e;
            }
        }
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StackClient.KAFKA_BOOTSTRAP);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "system-tests-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        eventProbe = new KafkaConsumer<>(consumerProps, new StringDeserializer(), new StringDeserializer());
        eventProbe.assign(List.of(PARTITION));
    }

    @AfterAll
    static void closeKafka() {
        eventProbe.close();
        admin.close();
    }

    @BeforeEach
    void skipToLatestEvent() {
        eventProbe.seekToEnd(List.of(PARTITION));
        eventProbe.position(PARTITION);
    }

    @Test
    @Order(1)
    void gatewayRejectsAnonymousCalls() throws Exception {
        assertThat(stack.get("/api/product", false).statusCode()).isEqualTo(401);
        assertThat(stack.post("/api/order", orderOf(line("iphone_15", "1.00", 1)), false).statusCode()).isEqualTo(401);
    }

    @Test
    @Order(2)
    void productCreatedThroughGatewayIsPersistedInMongoAndListed() throws Exception {
        String name = "System Test Phone " + UUID.randomUUID();
        String skuCode = "system_test_" + UUID.randomUUID();
        HttpResponse<String> created = stack.post("/api/product",
                Map.of("name", name, "description", "created by OrderFlowSystemIT", "price", 499.5, "skuCode", skuCode), true);
        assertThat(created.statusCode()).isEqualTo(201);

        HttpResponse<String> listed = stack.get("/api/product", true);
        assertThat(listed.statusCode()).isEqualTo(200);
        JsonNode match = null;
        for (JsonNode product : StackClient.JSON.readTree(listed.body())) {
            if (name.equals(product.get("name").asText())) {
                match = product;
            }
        }
        assertThat(match).isNotNull();
        assertThat(match.get("id").asText()).isNotBlank();
        assertThat(match.get("skuCode").asText()).isEqualTo(skuCode);
        assertThat(match.get("price").decimalValue()).isEqualByComparingTo("499.5");
    }

    @Test
    @Order(3)
    void inStockOrderIsPersistedPublishedAndConsumedByNotificationService() throws Exception {
        long lastOrderId = maxOrderId();

        HttpResponse<String> response = stack.post("/api/order", orderOf(line("iphone_15", "799.99", 2)), true);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo(ORDER_PLACED);

        List<String[]> rows = orderLinesAfter(lastOrderId);
        assertThat(rows).hasSize(1);
        String orderNumber = rows.get(0)[0];
        assertThat(rows.get(0)[1]).isEqualTo("iphone_15");
        assertThat(rows.get(0)[2]).isEqualTo("2");
        assertThat(new BigDecimal(rows.get(0)[3])).isEqualByComparingTo("799.99");

        List<String> events = pollEvents(Duration.ofSeconds(20), 1);
        assertThat(events).hasSize(1);
        assertThat(StackClient.JSON.readTree(events.get(0)).get("orderNumber").asText()).isEqualTo(orderNumber);

        long endOffset = eventProbe.endOffsets(List.of(PARTITION)).get(PARTITION);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OffsetAndMetadata committed = admin.listConsumerGroupOffsets(NOTIFICATION_GROUP)
                    .partitionsToOffsetAndMetadata().get().get(PARTITION);
            assertThat(committed).as("notification-service committed offset").isNotNull();
            assertThat(committed.offset()).isGreaterThanOrEqualTo(endOffset);
        });
    }

    @Test
    @Order(4)
    void outOfStockOrderIsRejectedWithNoRowAndNoEvent() throws Exception {
        long lastOrderId = maxOrderId();

        HttpResponse<String> response = stack.post("/api/order", orderOf(line("iphone_15_pro", "999.00", 1)), true);

        assertThat(response.body()).isEqualTo(ORDER_FALLBACK);
        assertThat(maxOrderId()).isEqualTo(lastOrderId);
        assertThat(pollEvents(Duration.ofSeconds(5), 1)).isEmpty();
    }

    @Test
    @Order(5)
    void mixedCartWithOneOutOfStockLineIsRejectedAtomically() throws Exception {
        long lastOrderId = maxOrderId();

        HttpResponse<String> response = stack.post("/api/order",
                orderOf(line("iphone_15", "799.99", 1), line("iphone_15_pro", "999.00", 1)), true);

        assertThat(response.body()).isEqualTo(ORDER_FALLBACK);
        assertThat(maxOrderId()).as("no partial order persisted").isEqualTo(lastOrderId);
        assertThat(pollEvents(Duration.ofSeconds(5), 1)).isEmpty();
    }

    @Test
    @Order(6)
    @EnabledIfEnvironmentVariable(named = "SYSTEM_TESTS_COMPOSE_FILE", matches = ".+")
    void inventoryOutageTriggersFallbackWithoutSideEffectsThenRecovers() throws Exception {
        long lastOrderId = maxOrderId();
        compose("stop", "inventory-service");
        try {
            HttpResponse<String> response = stack.post("/api/order", orderOf(line("iphone_15", "799.99", 1)), true);
            assertThat(response.body()).isEqualTo(ORDER_FALLBACK);
            assertThat(maxOrderId()).isEqualTo(lastOrderId);
            assertThat(pollEvents(Duration.ofSeconds(5), 1)).isEmpty();
        } finally {
            compose("start", "inventory-service");
        }

        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stack.post("/api/order", orderOf(line("iphone_15", "799.99", 1)), true).body())
                        .isEqualTo(ORDER_PLACED));
        // While inventory warms up, an attempt can exceed the 3s TimeLimiter: the caller gets the
        // fallback but the order still commits (the time limiter does not cancel the work). So the
        // recovery loop may leave more than one row, all for the requested line.
        assertThat(orderLinesAfter(lastOrderId)).isNotEmpty()
                .allSatisfy(row -> assertThat(row).containsSubsequence("iphone_15", "1"));
    }

    private static Map<String, Object> line(String skuCode, String price, int quantity) {
        return Map.of("skuCode", skuCode, "price", new BigDecimal(price), "quantity", quantity);
    }

    @SafeVarargs
    private static Map<String, Object> orderOf(Map<String, Object>... lines) {
        return Map.of("orderLineItemsDtoList", List.of(lines));
    }

    private static long maxOrderId() throws Exception {
        try (Connection db = StackClient.orderDb();
             ResultSet rs = db.createStatement().executeQuery("SELECT COALESCE(MAX(id), 0) FROM t_orders")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<String[]> orderLinesAfter(long orderId) throws Exception {
        String sql = "SELECT o.order_number, li.sku_code, li.quantity, li.price FROM t_orders o "
                + "JOIN t_orders_order_line_items_list j ON j.order_id = o.id "
                + "JOIN t_order_line_items li ON li.id = j.order_line_items_list_id WHERE o.id > ?";
        try (Connection db = StackClient.orderDb(); PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            List<String[]> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
            return rows;
        }
    }

    private static List<String> pollEvents(Duration window, int stopAfter) {
        List<String> events = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline && events.size() < stopAfter) {
            for (ConsumerRecord<String, String> record : eventProbe.poll(Duration.ofMillis(500))) {
                events.add(record.value());
            }
        }
        return events;
    }

    private static void compose(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-f", System.getenv("SYSTEM_TESTS_COMPOSE_FILE")));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
        builder.environment().putIfAbsent("OAUTH_CLIENT_SECRET", StackClient.clientSecret());
        Process process = builder.start();
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }
}
