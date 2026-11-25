package com.ibatulanand.notificationservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
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
