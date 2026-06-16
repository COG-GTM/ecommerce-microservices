package com.ibatulanand.notificationservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"notificationTopic"})
@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceApplicationTest {

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void handleNotification_receivesEvent(CapturedOutput output) throws Exception {
        OrderPlacedEvent event = new OrderPlacedEvent("order-123");
        kafkaTemplate.send("notificationTopic", event).get();

        String expected = "Received Notification for Order - order-123";
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && !output.getOut().contains(expected)) {
            Thread.sleep(200);
        }
        assertThat(output.getOut()).contains(expected);
    }

    @Test
    void main_runsSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(NotificationServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            NotificationServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(NotificationServiceApplication.class, new String[]{}));
        }
    }
}
