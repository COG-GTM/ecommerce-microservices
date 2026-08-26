package com.ibatulanand.notificationservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationListenerTest {

    private final NotificationServiceApplication application = new NotificationServiceApplication();

    @Test
    void should_handle_order_placed_event() {
        assertDoesNotThrow(() -> application.handleNotification(new OrderPlacedEvent("order-1")));
    }

    @Test
    void should_expose_order_number_from_event() {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderNumber("order-1");

        assertEquals("order-1", event.getOrderNumber());
        assertEquals(new OrderPlacedEvent("order-1"), event);
    }
}
