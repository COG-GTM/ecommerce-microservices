package com.ibatulanand.notificationservice;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceApplicationTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(NotificationServiceApplication.class);
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    @Test
    void handleNotificationLogsOrderNumber() {
        new NotificationServiceApplication().handleNotification(new OrderPlacedEvent("order-123"));

        assertThat(logAppender.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("order-123"));
    }
}
