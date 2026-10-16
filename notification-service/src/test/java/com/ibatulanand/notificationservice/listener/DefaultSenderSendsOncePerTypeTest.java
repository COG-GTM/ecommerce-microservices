package com.ibatulanand.notificationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibatulanand.notificationservice.sender.LoggingNotificationSender;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationSender;
import com.ibatulanand.notificationservice.sender.NotificationType;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * Runs the production wiring (only the default {@link LoggingNotificationSender}, no test sender) and checks that an
 * order with an EMAIL receipt and a ship-from-store line sends - and logs - each notification type exactly once.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {OrderPlacedListener.TOPIC, KafkaErrorHandlingConfig.DLT_TOPIC})
@ExtendWith(OutputCaptureExtension.class)
class DefaultSenderSendsOncePerTypeTest {

    private static final String ORDER = "5f21cc15-08cc-43d4-b1a3-dc777c0fdb7a";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ApplicationContext context;

    @SpyBean
    private LoggingNotificationSender sender;

    @Test
    void emailOrderWithShipFromStoreLineSendsOneReceiptOneFulfillmentAndOneShipNotice(CapturedOutput output) throws Exception {
        assertThat(context.getBeansOfType(NotificationSender.class)).hasSize(1);

        ObjectNode event = (ObjectNode) new ObjectMapper().readTree(new ClassPathResource("events/golden-cart-v2.json").getInputStream());
        event.put("orderNumber", ORDER);
        ObjectNode line = (ObjectNode) event.withArray("lineItems").get(1);
        line.put("fulfillmentType", "SHIP_FROM_STORE");
        line.put("fulfillmentStoreId", "1042");

        try (Producer<String, String> producer = new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer()).createProducer()) {
            ProducerRecord<String, String> record = new ProducerRecord<>(OrderPlacedListener.TOPIC, ORDER, event.toString());
            record.headers().add("__TypeId__", "event".getBytes(StandardCharsets.UTF_8));
            producer.send(record);
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(15)).until(() -> count(output.getAll(), "Sending SHIP_NOTICE for order " + ORDER) == 1);
        Thread.sleep(2000);

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender, atLeast(1)).send(sent.capture());
        List<NotificationType> types = sent.getAllValues().stream().map(Notification::type).toList();
        assertThat(types).containsExactly(NotificationType.RECEIPT, NotificationType.STORE_FULFILLMENT, NotificationType.SHIP_NOTICE);
        Notification receipt = sent.getAllValues().get(0);
        Notification shipNotice = sent.getAllValues().get(2);
        assertThat(receipt.subject()).startsWith("Your Gap receipt");
        assertThat(shipNotice.subject()).startsWith("Your Gap items will ship");
        assertThat(shipNotice.body()).contains("will ship to you", "High Rise Straight Jean").isNotEqualTo(receipt.body());

        String logs = output.getAll();
        assertThat(count(logs, "Received Notification for Order - " + ORDER)).isEqualTo(1);
        assertThat(count(logs, "Sending RECEIPT for order " + ORDER + " via EMAIL")).isEqualTo(1);
        assertThat(count(logs, "Sending STORE_FULFILLMENT for order " + ORDER + " via STORE to 1042")).isEqualTo(1);
        assertThat(count(logs, "Sending SHIP_NOTICE for order " + ORDER + " via EMAIL")).isEqualTo(1);
    }

    private static int count(String haystack, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
