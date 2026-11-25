package com.ibatulanand.notificationservice;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = NotificationPoisonRecordTest.TOPIC)
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class NotificationPoisonRecordTest {

    static final String TOPIC = "notificationTopic";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private EmbeddedKafkaBroker broker;

    private DefaultKafkaProducerFactory<String, String> producerFactory;
    private KafkaTemplate<String, String> rawTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerFactory = new DefaultKafkaProducerFactory<>(props);
        rawTemplate = new KafkaTemplate<>(producerFactory);
    }

    @AfterEach
    void tearDown() {
        producerFactory.destroy();
    }

    @Test
    void malformedRecordsDoNotBlockLaterOrderNotifications(CapturedOutput output) throws Exception {
        String plainText = "ord-" + UUID.randomUUID();
        String noTypeHeader = "ord-" + UUID.randomUUID();
        String wrongShape = "ord-" + UUID.randomUUID();
        String spoofedType = "ord-" + UUID.randomUUID();
        String tombstone = "ord-" + UUID.randomUUID();

        send("this is not json");
        send(event(plainText));
        send("{\"orderNumber\":{\"nested\":true}}");
        send(event(wrongShape));
        sendWithTypeHeader("{\"orderNumber\":\"x\"}", "java.util.ArrayList");
        send(event(spoofedType));
        send(null);
        send(event(tombstone));
        send(event(noTypeHeader));

        for (String orderNumber : new String[]{plainText, wrongShape, spoofedType, tombstone, noTypeHeader}) {
            awaitTrue(() -> output.getOut().contains("Received Notification for Order - " + orderNumber));
        }
    }

    @Test
    void orderServiceStyleEventWithTypeHeaderIsStillDelivered(CapturedOutput output) throws Exception {
        String orderNumber = "ord-" + UUID.randomUUID();

        sendWithTypeHeader(event(orderNumber), "event");

        awaitTrue(() -> output.getOut().contains("Received Notification for Order - " + orderNumber));
    }

    private static String event(String orderNumber) {
        return "{\"orderNumber\":\"" + orderNumber + "\"}";
    }

    private void send(String value) throws Exception {
        rawTemplate.send(TOPIC, value).get();
    }

    private void sendWithTypeHeader(String value, String typeId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, value);
        record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
        rawTemplate.send(record).get();
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                assertThat(condition.getAsBoolean()).as("condition met within %s", TIMEOUT).isTrue();
            }
            Thread.sleep(100);
        }
    }
}
