package com.ibatulanand.notificationservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    // Logs skipped records by position only: decode exceptions embed the raw payload bytes.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler((record, exception) -> log.error(
                "Skipping notification record {}-{}@{} after {} (cause: {})",
                record.topic(), record.partition(), record.offset(),
                exception.getClass().getName(),
                NestedExceptionUtils.getMostSpecificCause(exception).getClass().getName()));
    }

    @KafkaListener(topics = "notificationTopic")
    public void handleNotification(@Payload(required = false) OrderPlacedEvent orderPlacedEvent) {
        if (orderPlacedEvent == null || !StringUtils.hasText(orderPlacedEvent.getOrderNumber())) {
            log.warn("Skipping notification record without an order number");
            return;
        }
        // Send out an email notification
        log.info("Received Notification for Order - {}", orderPlacedEvent.getOrderNumber());
    }
}
