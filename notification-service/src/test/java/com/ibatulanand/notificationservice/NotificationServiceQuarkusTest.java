package com.ibatulanand.notificationservice;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import io.quarkus.test.junit.QuarkusTest;

import static org.awaitility.Awaitility.await;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationServiceQuarkusTest {
    private static final String TOPIC = "notificationTopic";
    private static final String TYPE_HEADER = "__TypeId__";
    private final CapturingHandler logHandler = new CapturingHandler();
    private Logger serviceLogger;

    @BeforeAll
    void attachLogHandler() {
        serviceLogger = org.jboss.logmanager.LogContext.getLogContext()
                .getLogger("com.ibatulanand.notificationservice");
        serviceLogger.addHandler(logHandler);
        serviceLogger.setLevel(Level.ALL);
        logHandler.setLevel(Level.ALL);
    }

    @AfterAll
    void detachLogHandler() {
        serviceLogger.removeHandler(logHandler);
    }

    @Test
    void consumesSpringProducerPayload() throws Exception {
        String orderNumber = "spring-interop";
        logHandler.clear();

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProperties.put("spring.json.type.mapping",
                "event:com.ibatulanand.orderservice.event.OrderPlacedEvent");

        DefaultKafkaProducerFactory<String, com.ibatulanand.orderservice.event.OrderPlacedEvent> factory =
                new DefaultKafkaProducerFactory<>(producerProperties);
        KafkaTemplate<String, com.ibatulanand.orderservice.event.OrderPlacedEvent> template =
                new KafkaTemplate<>(factory);
        template.send(TOPIC, "spring-key",
                new com.ibatulanand.orderservice.event.OrderPlacedEvent(orderNumber))
                .get(10, TimeUnit.SECONDS);
        template.flush();

        assertLogLine(orderNumber);
    }

    @Test
    void consumesRawWirePayloadWithSpringTypeHeader() throws Exception {
        String orderNumber = "abc-123";
        logHandler.clear();

        sendRaw("raw-key", "{\"orderNumber\":\"abc-123\"}".getBytes(StandardCharsets.UTF_8), true);

        assertLogLine(orderNumber);
    }

    @Test
    void toleratesUnknownPropertiesAndMissingTypeHeader() throws Exception {
        logHandler.clear();

        sendRaw("unknown-key",
                "{\"orderNumber\":\"unknown-property\",\"ignored\":true}".getBytes(StandardCharsets.UTF_8), true);
        sendRaw("missing-header", "{\"orderNumber\":\"no-type-header\"}".getBytes(StandardCharsets.UTF_8), false);

        assertLogLine("unknown-property");
        assertLogLine("no-type-header");
    }

    @Test
    void skipsNullAndGarbagePayloadsAndContinuesConsuming() throws Exception {
        logHandler.clear();

        sendRaw("null-key", null, true);
        sendRaw("garbage-key", "not-json".getBytes(StandardCharsets.UTF_8), true);
        sendRaw("valid-after-garbage", "{\"orderNumber\":\"after-garbage\"}".getBytes(StandardCharsets.UTF_8), true);

        assertLogLine("after-garbage");
    }

    @Test
    void exposesKafkaHealthAndPrometheusMetrics() {
        String healthBody = given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        assertTrue(healthBody.contains("SmallRye Reactive Messaging - readiness check"));
        assertTrue(healthBody.contains("notifications"));

        var metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String metricsBody = metricsResponse.asString();
        assertTrue(metricsResponse.getContentType().contains("text/plain")
                        || metricsResponse.getContentType().contains("openmetrics"),
                metricsResponse.getContentType());
        assertTrue(metricsBody.contains("# HELP") || metricsBody.contains("# TYPE"), metricsBody);
    }

    private void assertLogLine(String orderNumber) {
        String expected = "Received Notification for Order - " + orderNumber;
        await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> logHandler.messages().contains(expected));
    }

    private void sendRaw(String key, byte[] value, boolean includeTypeHeader) throws Exception {
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProperties)) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(TOPIC, key, value);
            if (includeTypeHeader) {
                record.headers().add(new RecordHeader(TYPE_HEADER, "event".getBytes(StandardCharsets.UTF_8)));
            }
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    private String bootstrapServers() {
        return ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                messages.add(record.getMessage());
            }
        }

        List<String> messages() {
            return new ArrayList<>(messages);
        }

        void clear() {
            messages.clear();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
