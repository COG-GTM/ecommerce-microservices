package com.ibatulanand.notificationservice.listener;

import com.ibatulanand.notificationservice.OrderPlacedEvent;
import com.ibatulanand.notificationservice.OrderPlacedEvent.ReceiptDelivery;
import com.ibatulanand.notificationservice.fulfillment.FulfillmentNotifier;
import com.ibatulanand.notificationservice.idempotency.NotificationDeduplicator;
import com.ibatulanand.notificationservice.receipt.ReceiptRenderer;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationSender;
import com.ibatulanand.notificationservice.sender.NotificationType;
import com.ibatulanand.notificationservice.support.LogText;
import com.ibatulanand.notificationservice.support.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
        log.info("Received Notification for Order - {}", LogText.oneLine(event.getOrderNumber()));
        if (event.effectiveSchemaVersion() < 2) {
            return;
        }
        validate(event);
        sendReceipt(event);
        fulfillmentNotifier.notifyFulfillment(event);
    }

    /** Rejects (to the DLT, without retries) v2 events that would produce a wrong receipt or an unroutable store request. */
    private static void validate(OrderPlacedEvent event) {
        if (isBlank(event.getOrderNumber())) {
            throw new InvalidOrderEventException("v2 OrderPlacedEvent without orderNumber");
        }
        String order = LogText.oneLine(event.getOrderNumber());
        if (event.getLineItems() == null || event.getLineItems().stream().noneMatch(Objects::nonNull)) {
            throw new InvalidOrderEventException("v2 OrderPlacedEvent " + order + " without lineItems");
        }
        if (event.getTotals() == null || event.getTotals().getTotal() == null) {
            throw new InvalidOrderEventException("v2 OrderPlacedEvent " + order + " without totals.total");
        }
        boolean unroutable = event.getLineItems().stream()
                .filter(line -> line != null && FulfillmentNotifier.SHIP_FROM_STORE.equals(line.getFulfillmentType()))
                .anyMatch(line -> isBlank(FulfillmentNotifier.fulfillingStore(event, line)));
        if (unroutable) {
            throw new InvalidOrderEventException("v2 OrderPlacedEvent " + order + " has a SHIP_FROM_STORE line without a store");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void sendReceipt(OrderPlacedEvent event) {
        ReceiptDelivery delivery = event.getReceiptDelivery();
        if (delivery == null || !delivery.isOutbound()) {
            log.info("Order {} receipt channel {} to {}; no outbound receipt", LogText.oneLine(event.getOrderNumber()),
                    delivery == null ? "NONE" : LogText.oneLine(delivery.getChannel()),
                    delivery == null ? "<none>" : PiiMasker.mask(delivery.getDestination()));
            return;
        }
        deduplicator.sendOnce(event.getOrderNumber(), NotificationType.RECEIPT.name(),
                () -> sender.send(new Notification(NotificationType.RECEIPT, event.getOrderNumber(),
                        delivery.getChannel(), delivery.getDestination(), receiptRenderer.subject(event),
                        receiptRenderer.render(event))));
    }
}
