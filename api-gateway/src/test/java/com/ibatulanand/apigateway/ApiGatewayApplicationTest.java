package com.ibatulanand.apigateway;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ApiGatewayApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            ApiGatewayApplication.main(args);
            spring.verify(() -> SpringApplication.run(ApiGatewayApplication.class, args));
        }
    }

    @Test
    void applicationClassIsInstantiableAsSpringConfiguration() {
        assertThat(new ApiGatewayApplication()).isNotNull();
    }
}
