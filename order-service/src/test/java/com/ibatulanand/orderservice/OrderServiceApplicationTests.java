package com.ibatulanand.orderservice;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void main_runsSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(OrderServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            OrderServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(OrderServiceApplication.class, new String[]{}));
        }
    }

}
