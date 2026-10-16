package com.ibatulanand.notificationservice.sender;

import com.ibatulanand.notificationservice.support.LogText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Default adapter: no external provider, the notification is written to the log with the recipient masked. */
@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(Notification notification) {
        log.info("Sending {} for order {} via {} to {}: {}\n{}",
                notification.type(), LogText.oneLine(notification.orderNumber()), LogText.oneLine(notification.channel()),
                LogText.oneLine(notification.maskedRecipient()), LogText.oneLine(notification.subject()),
                indent(notification.body()));
    }

    /** Body lines are prefixed so no line of a notification can pass for a separate log entry. */
    private static String indent(String body) {
        if (body == null) {
            return "    | ";
        }
        return body.lines().map(line -> "    | " + LogText.oneLine(line)).collect(Collectors.joining("\n"));
    }
}
