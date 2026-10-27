package com.ibatulanand.orderservice;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class OrderServiceApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            OrderServiceApplication.main(args);
            spring.verify(() -> SpringApplication.run(OrderServiceApplication.class, args));
        }
    }

    @Test
    void applicationClassIsInstantiableAsSpringConfiguration() {
        assertThat(new OrderServiceApplication()).isNotNull();
    }
}
