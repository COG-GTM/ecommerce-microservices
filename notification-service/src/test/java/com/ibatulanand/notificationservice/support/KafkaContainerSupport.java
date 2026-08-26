package com.ibatulanand.notificationservice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single Kafka container shared by every test in the module, started on first use and reused for
 * the lifetime of the JVM.
 */
public final class KafkaContainerSupport {

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    static {
        KAFKA.start();
    }

    private KafkaContainerSupport() {
    }

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
