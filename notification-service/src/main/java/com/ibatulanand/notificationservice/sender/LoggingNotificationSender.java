package com.ibatulanand.notificationservice.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Default adapter: no external provider, the notification is written to the log with the recipient masked. */
@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(Notification notification) {
        log.info("Sending {} for order {} via {} to {}: {}\n{}",
                notification.type(), notification.orderNumber(), notification.channel(),
                notification.maskedRecipient(), notification.subject(), notification.body());
    }
}
