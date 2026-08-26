package com.ibatulanand.notificationservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for full-context tests exercising the Kafka listener against a real broker. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        KafkaContainerSupport.registerProperties(registry);
    }
}
