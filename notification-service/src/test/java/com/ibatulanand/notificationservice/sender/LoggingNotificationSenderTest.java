package com.ibatulanand.notificationservice.sender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingNotificationSenderTest {

    @Test
    void eventTextCannotForgeLogLines(CapturedOutput output) {
        new LoggingNotificationSender().send(new Notification(NotificationType.RECEIPT, "O-1\nFORGED order paid",
                "EMAIL", "jordan.lee@example.com", "Receipt\r\nFORGED subject", "line one\nline two"));

        assertThat(output.getAll())
                .contains("for order O-1_FORGED order paid via EMAIL to j***@e***.com: Receipt__FORGED subject")
                .contains("    | line one\n    | line two")
                .doesNotContain("jordan.lee@example.com");
        assertThat(output.getAll().lines()).noneMatch(line -> line.startsWith("FORGED"));
    }
}
