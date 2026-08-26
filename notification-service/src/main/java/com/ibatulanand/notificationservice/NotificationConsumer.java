package com.ibatulanand.notificationservice;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NotificationConsumer {
    private static final Logger LOG = Logger.getLogger(NotificationConsumer.class);

    @Incoming("notifications")
    public void handleNotification(OrderPlacedEvent orderPlacedEvent) {
        if (orderPlacedEvent == null) {
            return;
        }

        LOG.infof("Received Notification for Order - %s", orderPlacedEvent.getOrderNumber());
    }
}
