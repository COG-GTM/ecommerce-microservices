package com.ibatulanand.notificationservice.sender;

import com.ibatulanand.notificationservice.support.LogText;
import com.ibatulanand.notificationservice.support.PiiMasker;

/**
 * An outbound notification. {@code channel} is {@code EMAIL}, {@code SMS} or {@code STORE};
 * {@code recipient} is the customer's email/phone, or the fulfilling store id for {@code STORE}.
 */
public record Notification(NotificationType type,
                           String orderNumber,
                           String channel,
                           String recipient,
                           String subject,
                           String body) {

    public String maskedRecipient() {
        return "STORE".equals(channel) ? recipient : PiiMasker.mask(recipient);
    }

    @Override
    public String toString() {
        return "Notification(type=" + type + ", orderNumber=" + LogText.oneLine(orderNumber) + ", channel="
                + LogText.oneLine(channel) + ", recipient=" + LogText.oneLine(maskedRecipient()) + ", subject="
                + LogText.oneLine(subject) + ")";
    }
}
