package com.ibatulanand.notificationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "eureka.client.enabled=false",
        "management.tracing.enabled=false",
        "notification.kafka.retry.interval-ms=50",
        "notification.kafka.retry.max-retries=2"
})
@EmbeddedKafka(partitions = 1, topics = {OrderPlacedListener.TOPIC, KafkaErrorHandlingConfig.DLT_TOPIC})
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderPlacedListenerIntegrationTest {

    private static final Duration SETTLE = Duration.ofSeconds(2);
    private static final String EMAIL = "jordan.lee@example.com";
    private static final String PHONE = "+1 (415) 555-0142";

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        RecordingNotificationSender recordingNotificationSender() {
            return new RecordingNotificationSender();
        }
    }

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private RecordingNotificationSender sender;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ConsumerRecord<String, byte[]>> deadLetters = new CopyOnWriteArrayList<>();
    private Producer<String, String> producer;
    private Consumer<String, byte[]> dltConsumer;

    @BeforeAll
    void setUp() {
        producer = new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer()).createProducer();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-reader", "true", broker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dltConsumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(),
                new ByteArrayDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, KafkaErrorHandlingConfig.DLT_TOPIC);
    }

    @AfterAll
    void tearDown() {
        producer.close();
        dltConsumer.close();
    }

    @Test
    void goldenCartV2WithEmailSendsExactlyOneReceipt() throws IOException {
        String orderNumber = "POS-GOLDEN-1";
        publish(orderNumber, event("golden-cart-v2.json", orderNumber));

        await().atMost(Duration.ofSeconds(15)).until(() -> sender.sentFor(orderNumber).size() == 1);
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2)).until(() -> sender.sentFor(orderNumber).size() == 1);

        Notification receipt = sender.sentFor(orderNumber).get(0);
        assertThat(receipt.type()).isEqualTo(NotificationType.RECEIPT);
        assertThat(receipt.channel()).isEqualTo("EMAIL");
        assertThat(receipt.recipient()).isEqualTo(EMAIL);
        assertThat(receipt.body())
                .contains(orderNumber, "268341-016-L", "471902-004-29", "512884-022-M", "FALL30")
                .containsPattern("Sales tax 8\\.625% +9\\.34")
                .containsPattern("TOTAL +117\\.65")
                .contains("Store 1969", "Register 04", "Associate A-4471", "FINAL SALE", "Credit/Debit");
    }

    @Test
    void shipFromStoreLineSendsOneStoreFulfillmentAndOneShipNotice() throws IOException {
        String orderNumber = "POS-SFS-1";
        publish(orderNumber, event("ship-from-store-v2.json", orderNumber));

        await().atMost(Duration.ofSeconds(15)).until(() -> sender.sentFor(orderNumber).size() == 3);
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2)).until(() -> sender.sentFor(orderNumber).size() == 3);

        List<Notification> fulfillment = sender.sentFor(orderNumber, NotificationType.STORE_FULFILLMENT);
        assertThat(fulfillment).hasSize(1);
        assertThat(fulfillment.get(0).recipient()).isEqualTo("1042");
        assertThat(fulfillment.get(0).channel()).isEqualTo("STORE");
        assertThat(fulfillment.get(0).body()).contains("471902-004-29").doesNotContain("268341-016-L");

        List<Notification> shipNotice = sender.sentFor(orderNumber, NotificationType.SHIP_NOTICE);
        assertThat(shipNotice).hasSize(1);
        assertThat(shipNotice.get(0).channel()).isEqualTo("SMS");
        assertThat(shipNotice.get(0).body()).contains("High Rise Straight Jean");

        assertThat(sender.sentFor(orderNumber, NotificationType.RECEIPT)).hasSize(1);
    }

    @Test
    void v1PayloadIsConsumedWithoutError(CapturedOutput output) {
        String orderNumber = "V1-ORDER-1";
        publish(orderNumber, "{\"orderNumber\":\"" + orderNumber + "\"}");

        await().atMost(Duration.ofSeconds(15))
                .until(() -> output.getOut().contains("Received Notification for Order - " + orderNumber));
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2)).until(() -> deadLetter(orderNumber).isEmpty());
        assertThat(sender.sentFor(orderNumber)).isEmpty();
        assertThat(output.getOut()).doesNotContain("ERROR");
    }

    @Test
    void v2PayloadWithUnknownFieldsIsStillConsumed() throws IOException {
        String orderNumber = "POS-FORWARD-1";
        ObjectNode event = eventNode("golden-cart-v2.json", orderNumber);
        event.put("loyaltyTier", "ICON");
        ((ObjectNode) event.get("lineItems").get(0)).put("rfidTag", "E200-1234");
        publish(orderNumber, event.toString());

        await().atMost(Duration.ofSeconds(15)).until(() -> sender.sentFor(orderNumber, NotificationType.RECEIPT).size() == 1);
    }

    @Test
    void duplicateEventSendsOneReceipt(CapturedOutput output) throws IOException {
        String orderNumber = "POS-DUP-1";
        String payload = event("golden-cart-v2.json", orderNumber);
        publish(orderNumber, payload);
        publish(orderNumber, payload);

        await().atMost(Duration.ofSeconds(15))
                .until(() -> output.getOut().contains("Skipping duplicate RECEIPT for order " + orderNumber));
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2)).until(() -> sender.sentFor(orderNumber).size() == 1);
        assertThat(sender.sentFor(orderNumber, NotificationType.RECEIPT)).hasSize(1);
    }

    @Test
    void printAndNoneChannelsSendNothing() throws IOException {
        for (String channel : List.of("PRINT", "NONE")) {
            String orderNumber = "POS-" + channel + "-1";
            ObjectNode event = eventNode("golden-cart-v2.json", orderNumber);
            ((ObjectNode) event.get("receiptDelivery")).put("channel", channel);
            publish(orderNumber, event.toString());
        }
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2))
                .until(() -> sender.sentFor("POS-PRINT-1").isEmpty() && sender.sentFor("POS-NONE-1").isEmpty());
    }

    @Test
    void malformedPayloadGoesToDlt() {
        String key = "MALFORMED-1";
        String payload = "{\"orderNumber\": \"MALFORMED-1\", \"lineItems\": [ this is not json";
        publish(key, payload);

        ConsumerRecord<String, byte[]> dead = await().atMost(Duration.ofSeconds(15))
                .until(() -> deadLetter(key), Optional::isPresent).get();
        assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo(payload);
        assertThat(header(dead, "kafka_dlt-exception-fqcn")).isEqualTo(DeserializationException.class.getName());
        assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(OrderPlacedListener.TOPIC);
    }

    @Test
    void incompleteV2EventsGoToDltWithoutRetryOrSending(CapturedOutput output) throws IOException {
        ObjectNode noTotals = eventNode("golden-cart-v2.json", "POS-NO-TOTALS");
        noTotals.remove("totals");
        ObjectNode noLines = eventNode("golden-cart-v2.json", "POS-NO-LINES");
        noLines.remove("lineItems");
        ObjectNode noStore = eventNode("ship-from-store-v2.json", "POS-NO-STORE");
        noStore.putNull("storeId");
        for (var line : noStore.get("lineItems")) {
            ((ObjectNode) line).putNull("fulfillmentStoreId");
        }
        for (ObjectNode event : List.of(noTotals, noLines, noStore)) {
            publish(event.get("orderNumber").asText(), event.toString());
        }

        for (String orderNumber : List.of("POS-NO-TOTALS", "POS-NO-LINES", "POS-NO-STORE")) {
            ConsumerRecord<String, byte[]> dead = await().atMost(Duration.ofSeconds(15))
                    .until(() -> deadLetter(orderNumber), Optional::isPresent).get();
            assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(InvalidOrderEventException.class.getName());
            assertThat(sender.attempts(orderNumber)).isZero();
            assertThat(output.getOut().split("Received Notification for Order - " + orderNumber + "\\R", -1)).hasSize(2);
        }
    }

    @Test
    void invalidContactDestinationSendsNoCustomerNotification() throws IOException {
        ObjectNode badEmail = eventNode("golden-cart-v2.json", "POS-BAD-EMAIL");
        ((ObjectNode) badEmail.get("receiptDelivery")).put("destination", "not-an-email");
        ObjectNode badPhone = eventNode("ship-from-store-v2.json", "POS-BAD-SMS");
        ((ObjectNode) badPhone.get("receiptDelivery")).put("destination", "call me");
        publish("POS-BAD-EMAIL", badEmail.toString());
        publish("POS-BAD-SMS", badPhone.toString());

        await().atMost(Duration.ofSeconds(15))
                .until(() -> sender.sentFor("POS-BAD-SMS", NotificationType.STORE_FULFILLMENT).size() == 1);
        await().during(SETTLE).atMost(SETTLE.plusSeconds(2)).until(() -> sender.sentFor("POS-BAD-EMAIL").isEmpty()
                && sender.sentFor("POS-BAD-SMS", NotificationType.RECEIPT).isEmpty()
                && sender.sentFor("POS-BAD-SMS", NotificationType.SHIP_NOTICE).isEmpty());
    }

    @Test
    void senderFailureIsRetriedBoundedTimesThenGoesToDlt() throws IOException {
        String orderNumber = "FAIL-PROVIDER-1";
        publish(orderNumber, event("golden-cart-v2.json", orderNumber));

        await().atMost(Duration.ofSeconds(15)).until(() -> deadLetter(orderNumber).isPresent());
        assertThat(sender.attempts(orderNumber)).isEqualTo(3);
        assertThat(sender.sentFor(orderNumber)).isEmpty();
    }

    @Test
    void logsNeverContainRawEmailOrPhone(CapturedOutput output) throws IOException {
        publish("POS-PII-EMAIL", event("golden-cart-v2.json", "POS-PII-EMAIL"));
        publish("POS-PII-SMS", event("ship-from-store-v2.json", "POS-PII-SMS"));

        await().atMost(Duration.ofSeconds(15)).until(() -> sender.sentFor("POS-PII-EMAIL").size() == 1
                && sender.sentFor("POS-PII-SMS").size() == 3);

        String logs = output.getAll();
        assertThat(logs).contains("Sending RECEIPT for order POS-PII-EMAIL via EMAIL to j***@e***.com");
        assertThat(logs).contains("via SMS to ***-***-0142");
        assertThat(logs).doesNotContain(EMAIL, "jordan.lee", "example.com", PHONE, "555-0142", "4155550142");
    }

    private void publish(String key, String json) {
        ProducerRecord<String, String> record = new ProducerRecord<>(OrderPlacedListener.TOPIC, key, json);
        record.headers().add("__TypeId__", "event".getBytes(StandardCharsets.UTF_8));
        producer.send(record);
        producer.flush();
    }

    private String event(String fixture, String orderNumber) throws IOException {
        return eventNode(fixture, orderNumber).toString();
    }

    private ObjectNode eventNode(String fixture, String orderNumber) throws IOException {
        ObjectNode node = (ObjectNode) mapper.readTree(new ClassPathResource("events/" + fixture).getInputStream());
        node.put("orderNumber", orderNumber);
        return node;
    }

    private Optional<ConsumerRecord<String, byte[]>> deadLetter(String key) {
        KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(200)).forEach(deadLetters::add);
        return deadLetters.stream().filter(r -> key.equals(r.key())).findFirst();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
