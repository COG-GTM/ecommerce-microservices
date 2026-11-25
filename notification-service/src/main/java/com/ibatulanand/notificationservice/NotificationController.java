package com.ibatulanand.notificationservice;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationLog notificationLog;

    @GetMapping
    public List<NotificationLog.Notification> recent() {
        return notificationLog.recent();
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<NotificationLog.Notification> byOrderNumber(@PathVariable String orderNumber) {
        return notificationLog.find(orderNumber).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
