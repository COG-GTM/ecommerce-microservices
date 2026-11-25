package com.ibatulanand.notificationservice;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Bounded in-memory log of consumed order events, exposed read-only so E2E tests can assert delivery. */
@Component
public class NotificationLog {

    public record Notification(String orderNumber, String channel, String storeNumber, String tillId,
                               String transactionId, String tenderType, Integer totalPence, String message, Instant receivedAt) {
    }

    private static final int CAPACITY = 200;
    private final Deque<Notification> entries = new ArrayDeque<>();

    public synchronized Notification record(OrderPlacedEvent event) {
        Notification notification = new Notification(event.getOrderNumber(), event.getChannel(),
                event.getStoreNumber(), event.getTillId(), event.getTransactionId(),
                event.getTenderType(), event.getTotalPence(),
                "Received Notification for Order - " + event.getOrderNumber(), Instant.now());
        entries.addFirst(notification);
        while (entries.size() > CAPACITY) {
            entries.removeLast();
        }
        return notification;
    }

    public synchronized List<Notification> recent() {
        return new ArrayList<>(entries);
    }

    public synchronized Optional<Notification> find(String orderNumber) {
        return entries.stream().filter(n -> n.orderNumber().equals(orderNumber)).findFirst();
    }
}
