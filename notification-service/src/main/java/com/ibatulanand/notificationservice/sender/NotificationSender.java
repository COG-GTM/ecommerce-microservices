package com.ibatulanand.notificationservice.sender;

/** Outbound port for receipts and fulfillment notifications. */
public interface NotificationSender {

    void send(Notification notification);
}
