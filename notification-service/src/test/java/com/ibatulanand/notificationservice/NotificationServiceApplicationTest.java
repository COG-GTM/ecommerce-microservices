package com.ibatulanand.notificationservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            NotificationServiceApplication.main(args);
            spring.verify(() -> SpringApplication.run(NotificationServiceApplication.class, args));
        }
    }

    @Test
    void handleNotificationLogsTheOrderNumber(CapturedOutput output) {
        new NotificationServiceApplication().handleNotification(new OrderPlacedEvent("order-123"));

        assertThat(output).contains("Received Notification for Order - order-123");
    }
}
