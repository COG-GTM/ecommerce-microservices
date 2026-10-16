package com.ibatulanand.notificationservice.listener;

import com.ibatulanand.notificationservice.OrderPlacedEvent;
import com.ibatulanand.notificationservice.OrderPlacedEvent.ReceiptDelivery;
import com.ibatulanand.notificationservice.fulfillment.FulfillmentNotifier;
import com.ibatulanand.notificationservice.idempotency.NotificationDeduplicator;
import com.ibatulanand.notificationservice.receipt.ReceiptRenderer;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationSender;
import com.ibatulanand.notificationservice.sender.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedListener {

    public static final String TOPIC = "notificationTopic";

    private final ReceiptRenderer receiptRenderer;
    private final FulfillmentNotifier fulfillmentNotifier;
    private final NotificationSender sender;
    private final NotificationDeduplicator deduplicator;

    @KafkaListener(topics = TOPIC, groupId = "notificationId")
    public void handleNotification(OrderPlacedEvent event) {
        log.info("Received Notification for Order - {}", event.getOrderNumber());
        if (event.effectiveSchemaVersion() < 2) {
            return;
        }
        if (event.getOrderNumber() == null || event.getOrderNumber().isBlank()) {
            throw new InvalidOrderEventException("v2 OrderPlacedEvent without orderNumber");
        }
        sendReceipt(event);
        fulfillmentNotifier.notifyFulfillment(event);
    }

    private void sendReceipt(OrderPlacedEvent event) {
        ReceiptDelivery delivery = event.getReceiptDelivery();
        if (delivery == null || !delivery.isOutbound()) {
            log.info("Order {} receipt channel {}; no outbound receipt", event.getOrderNumber(),
                    delivery == null ? "NONE" : delivery.getChannel());
            return;
        }
        deduplicator.sendOnce(event.getOrderNumber(), NotificationType.RECEIPT.name(),
                () -> sender.send(new Notification(NotificationType.RECEIPT, event.getOrderNumber(),
                        delivery.getChannel(), delivery.getDestination(), receiptRenderer.subject(event),
                        receiptRenderer.render(event))));
    }
}
