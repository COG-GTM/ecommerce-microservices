package com.ibatulanand.notificationservice;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false",
        "spring.kafka.consumer.auto-offset-reset=earliest"})
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceApplicationIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void consumesOrderPlacedEventPublishedWithOrderServiceWireFormat(CapturedOutput output) {
        // Same serializer + type mapping the order-service producer uses, so this exercises the real contract.
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                JsonSerializer.TYPE_MAPPINGS, "event:com.ibatulanand.notificationservice.OrderPlacedEvent");
        KafkaTemplate<String, OrderPlacedEvent> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
        String orderNumber = UUID.randomUUID().toString();

        template.send("notificationTopic", new OrderPlacedEvent(orderNumber));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(output).contains("Received Notification for Order - " + orderNumber));
    }
}
