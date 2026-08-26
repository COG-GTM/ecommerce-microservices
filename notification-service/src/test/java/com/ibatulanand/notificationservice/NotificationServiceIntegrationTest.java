package com.ibatulanand.notificationservice;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.1"));

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private final Logger logger = (Logger) LoggerFactory.getLogger(NotificationServiceApplication.class);
    private ListAppender<ILoggingEvent> logAppender;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer", () -> StringSerializer.class.getName());
        registry.add("spring.kafka.producer.value-serializer", () -> JsonSerializer.class.getName());
        registry.add("spring.kafka.producer.properties.spring.json.type.mapping",
                () -> "event:com.ibatulanand.notificationservice.OrderPlacedEvent");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("management.tracing.sampling.probability", () -> 0.0);
    }

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        logAppender.list = Collections.synchronizedList(new ArrayList<>());
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    @Test
    void notificationIsConsumedFromKafka() {
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent("order-456"));

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    List<ILoggingEvent> snapshot;
                    synchronized (logAppender.list) {
                        snapshot = new ArrayList<>(logAppender.list);
                    }
                    assertThat(snapshot)
                            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("order-456"));
                });
    }
}
