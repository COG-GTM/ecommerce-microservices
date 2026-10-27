package com.ibatulanand.productservice;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ProductServiceApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--server.port=0"};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            ProductServiceApplication.main(args);
            spring.verify(() -> SpringApplication.run(ProductServiceApplication.class, args));
        }
    }

    @Test
    void applicationClassIsInstantiableAsSpringConfiguration() {
        assertThat(new ProductServiceApplication()).isNotNull();
    }
}
