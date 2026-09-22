package com.ibatulanand.notificationservice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NotificationServiceApplicationTests {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(NotificationServiceApplication.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void handleNotificationLogsOrderNumber() {
        NotificationServiceApplication application = new NotificationServiceApplication();

        application.handleNotification(new OrderPlacedEvent("order-123"));

        Assertions.assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        Assertions.assertEquals(Level.INFO, event.getLevel());
        Assertions.assertEquals("Received Notification for Order - order-123", event.getFormattedMessage());
    }

    @Test
    void handleNotificationLogsNullOrderNumber() {
        NotificationServiceApplication application = new NotificationServiceApplication();

        application.handleNotification(new OrderPlacedEvent());

        Assertions.assertEquals(1, listAppender.list.size());
        Assertions.assertEquals("Received Notification for Order - null", listAppender.list.get(0).getFormattedMessage());
    }

    @Test
    void orderPlacedEventHoldsOrderNumber() {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderNumber("abc");

        Assertions.assertEquals("abc", event.getOrderNumber());
        Assertions.assertEquals(new OrderPlacedEvent("abc"), event);
        Assertions.assertTrue(event.toString().contains("abc"));
    }
}
