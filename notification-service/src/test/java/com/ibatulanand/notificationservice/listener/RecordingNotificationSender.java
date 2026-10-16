package com.ibatulanand.notificationservice.listener;

import com.ibatulanand.notificationservice.sender.LoggingNotificationSender;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationSender;
import com.ibatulanand.notificationservice.sender.NotificationType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Records every notification (and still logs it through the default adapter). Orders starting with {@code FAIL-} always throw. */
class RecordingNotificationSender implements NotificationSender {

    private final LoggingNotificationSender delegate = new LoggingNotificationSender();
    private final List<Notification> sent = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Override
    public void send(Notification notification) {
        attempts.computeIfAbsent(notification.orderNumber(), k -> new AtomicInteger()).incrementAndGet();
        if (notification.orderNumber().startsWith("FAIL-")) {
            throw new IllegalStateException("provider down for " + notification.orderNumber());
        }
        delegate.send(notification);
        sent.add(notification);
    }

    List<Notification> sentFor(String orderNumber) {
        return sent.stream().filter(n -> n.orderNumber().equals(orderNumber)).toList();
    }

    List<Notification> sentFor(String orderNumber, NotificationType type) {
        return sentFor(orderNumber).stream().filter(n -> n.type() == type).toList();
    }

    int attempts(String orderNumber) {
        AtomicInteger count = attempts.get(orderNumber);
        return count == null ? 0 : count.get();
    }
}
