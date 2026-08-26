package com.ibatulanand.notificationservice;

import com.ibatulanand.notificationservice.support.AbstractIntegrationTest;
import com.ibatulanand.notificationservice.support.KafkaContainerSupport;
import com.ibatulanand.notificationservice.support.TestFixtures;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JSON type mapping contract between order-service's producer, which tags the payload
 * with the type id {@code event}, and this consumer, which maps that id onto its own
 * {@link OrderPlacedEvent}.
 */
class NotificationListenerTest extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @SpyBean
    private NotificationServiceApplication listener;

    private KafkaTemplate<String, String> producerTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaContainerSupport.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(producerFactory);
    }

    @Test
    void should_receive_order_placed_event_when_producer_tags_payload_with_mapped_type_id() {
        String orderNumber = UUID.randomUUID().toString();

        producerTemplate().send(TestFixtures.orderPlacedRecord(orderNumber));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        Awaitility.await()
                .atMost(TIMEOUT)
                .untilAsserted(
                        () -> {
                            Mockito.verify(listener, Mockito.atLeastOnce())
                                    .handleNotification(captor.capture());
                            assertThat(captor.getAllValues())
                                    .extracting(OrderPlacedEvent::getOrderNumber)
                                    .contains(orderNumber);
                        });
    }

    @Test
    void should_deserialize_every_event_when_multiple_orders_are_published() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        KafkaTemplate<String, String> template = producerTemplate();
        template.send(TestFixtures.orderPlacedRecord(first));
        template.send(TestFixtures.orderPlacedRecord(second));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        Awaitility.await()
                .atMost(TIMEOUT)
                .untilAsserted(
                        () -> {
                            Mockito.verify(listener, Mockito.atLeastOnce())
                                    .handleNotification(captor.capture());
                            assertThat(captor.getAllValues())
                                    .extracting(OrderPlacedEvent::getOrderNumber)
                                    .contains(first, second);
                        });
    }
}
