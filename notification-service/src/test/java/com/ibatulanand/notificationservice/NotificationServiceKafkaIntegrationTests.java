package com.ibatulanand.notificationservice;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"notificationTopic"})
class NotificationServiceKafkaIntegrationTests {

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @SpyBean
    private NotificationServiceApplication notificationServiceApplication;

    @Test
    void listenerReceivesOrderPlacedEventFromNotificationTopic() {
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent("order-456"));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(notificationServiceApplication, timeout(10_000)).handleNotification(captor.capture());
        assertEquals("order-456", captor.getValue().getOrderNumber());
    }

    @Test
    void contextLoads() {
        verify(notificationServiceApplication, timeout(1_000).times(0)).handleNotification(any());
    }
}
