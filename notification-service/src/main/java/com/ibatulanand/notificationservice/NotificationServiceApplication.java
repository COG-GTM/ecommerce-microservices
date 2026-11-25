package com.ibatulanand.notificationservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;

@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @KafkaListener(topics = "notificationTopic")
    public void handleNotification(@Payload(required = false) OrderPlacedEvent orderPlacedEvent) {
        if (orderPlacedEvent == null) {
            log.warn("Skipping notification record with no order event");
            return;
        }
        // Send out an email notification
        log.info("Received Notification for Order - {}", orderPlacedEvent.getOrderNumber());
    }
}
