package com.ibatulanand.notificationservice;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    // Exception messages may embed the record payload; only OrderPlacedEventDeserializer's are payload-free.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
            log.error("Skipping notification record {}-{}@{} after {} (cause: {})",
                    record.topic(), record.partition(), record.offset(),
                    exception.getClass().getName(),
                    cause instanceof SerializationException ? cause.getMessage() : cause.getClass().getName());
        });
        // Keep the rest of the poll in memory; seeking back would re-fetch and re-decode it after every bad record.
        handler.setSeekAfterError(false);
        return handler;
    }

    @KafkaListener(topics = "notificationTopic")
    public void handleNotification(@Payload(required = false) OrderPlacedEvent orderPlacedEvent,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset) {
        if (orderPlacedEvent == null || !StringUtils.hasText(orderPlacedEvent.getOrderNumber())) {
            log.warn("Skipping notification record {}-{}@{} without an order number", topic, partition, offset);
            return;
        }
        // Send out an email notification
        log.info("Received Notification for Order - {}",
                orderPlacedEvent.getOrderNumber().replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", "_"));
    }
}
