package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class DiscoveryServerApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            DiscoveryServerApplication.main(args);
            spring.verify(() -> SpringApplication.run(DiscoveryServerApplication.class, args));
        }
    }

    @Test
    void applicationClassIsInstantiableAsSpringConfiguration() {
        assertThat(new DiscoveryServerApplication()).isNotNull();
    }
}
