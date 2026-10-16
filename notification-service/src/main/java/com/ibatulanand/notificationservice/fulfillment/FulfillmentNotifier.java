package com.ibatulanand.notificationservice.fulfillment;

import com.ibatulanand.notificationservice.OrderPlacedEvent;
import com.ibatulanand.notificationservice.OrderPlacedEvent.LineItem;
import com.ibatulanand.notificationservice.OrderPlacedEvent.ReceiptDelivery;
import com.ibatulanand.notificationservice.idempotency.NotificationDeduplicator;
import com.ibatulanand.notificationservice.sender.Notification;
import com.ibatulanand.notificationservice.sender.NotificationSender;
import com.ibatulanand.notificationservice.sender.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groups {@code SHIP_FROM_STORE} lines by {@code fulfillmentStoreId}, sends one pick-pack-ship request per
 * fulfilling store, and one "your items will ship" notice to the customer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentNotifier {

    public static final String SHIP_FROM_STORE = "SHIP_FROM_STORE";
    public static final String STORE_CHANNEL = "STORE";

    private final NotificationSender sender;
    private final NotificationDeduplicator deduplicator;

    public void notifyFulfillment(OrderPlacedEvent event) {
        Map<String, List<LineItem>> byStore = shipFromStoreLines(event);
        if (byStore.isEmpty()) {
            return;
        }
        String orderNumber = event.getOrderNumber();
        byStore.forEach((storeId, lines) -> deduplicator.sendOnce(orderNumber,
                NotificationType.STORE_FULFILLMENT + ":" + storeId,
                () -> sender.send(new Notification(NotificationType.STORE_FULFILLMENT, orderNumber, STORE_CHANNEL,
                        storeId, "Pick-pack-ship: order " + orderNumber, storeRequest(event, storeId, lines)))));

        ReceiptDelivery delivery = event.getReceiptDelivery();
        if (delivery == null || !delivery.isOutbound()) {
            log.info("Order {} has ship-from-store lines but no customer email/SMS; skipping ship notice", orderNumber);
            return;
        }
        deduplicator.sendOnce(orderNumber, NotificationType.SHIP_NOTICE.name(),
                () -> sender.send(new Notification(NotificationType.SHIP_NOTICE, orderNumber, delivery.getChannel(),
                        delivery.getDestination(), "Your Gap items will ship - order " + orderNumber,
                        shipNotice(orderNumber, byStore))));
    }

    static Map<String, List<LineItem>> shipFromStoreLines(OrderPlacedEvent event) {
        if (event.getLineItems() == null) {
            return Map.of();
        }
        return event.getLineItems().stream()
                .filter(line -> line != null && SHIP_FROM_STORE.equals(line.getFulfillmentType()))
                .collect(Collectors.groupingBy(line -> fulfillingStore(event, line), LinkedHashMap::new, Collectors.toList()));
    }

    private static String fulfillingStore(OrderPlacedEvent event, LineItem line) {
        String storeId = line.getFulfillmentStoreId();
        return storeId == null || storeId.isBlank() ? event.getStoreId() : storeId;
    }

    private static String storeRequest(OrderPlacedEvent event, String storeId, List<LineItem> lines) {
        StringBuilder body = new StringBuilder()
                .append("Store ").append(storeId).append(": pick, pack and ship for order ").append(event.getOrderNumber())
                .append(" (sold at store ").append(event.getStoreId()).append(", register ").append(event.getRegisterId())
                .append(")\n");
        for (LineItem line : lines) {
            body.append("  ").append(line.getQuantity() == null ? 1 : line.getQuantity()).append(" x ")
                    .append(line.getSkuCode()).append("  ").append(line.getDescription());
            if (line.getColorName() != null || line.getSize() != null) {
                body.append(" (").append(line.getColorName()).append(" / ").append(line.getSize()).append(')');
            }
            body.append('\n');
        }
        return body.toString();
    }

    private static String shipNotice(String orderNumber, Map<String, List<LineItem>> byStore) {
        StringBuilder body = new StringBuilder("Good news! These items from order ").append(orderNumber)
                .append(" will ship to you:\n");
        byStore.values().stream().flatMap(List::stream).forEach(line -> body.append("  ")
                .append(line.getQuantity() == null ? 1 : line.getQuantity()).append(" x ").append(line.getDescription())
                .append('\n'));
        return body.toString();
    }
}
