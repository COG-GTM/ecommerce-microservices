package com.ibatulanand.orderservice.integration;

import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {OrderPlacedEventKafkaIntegrationTest.TOPIC})
class OrderPlacedEventKafkaIntegrationTest {

    static final String TOPIC = "notificationTopic";

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, OrderPlacedEvent> consumer;

    @BeforeEach
    void createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("order-test-group", "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<OrderPlacedEvent> valueDeserializer = new JsonDeserializer<>(OrderPlacedEvent.class);
        valueDeserializer.addTrustedPackages("com.ibatulanand.orderservice.event");
        valueDeserializer.setUseTypeHeaders(false);
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer)
                .createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    @Test
    void orderPlacedEventIsPublishedAndConsumedFromNotificationTopic() throws Exception {
        OrderPlacedEvent event = new OrderPlacedEvent("order-123");

        kafkaTemplate.send(TOPIC, event).get(10, java.util.concurrent.TimeUnit.SECONDS);

        ConsumerRecord<String, OrderPlacedEvent> record =
                KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.value()).isNotNull();
        assertThat(record.value().getOrderNumber()).isEqualTo("order-123");
    }
}
