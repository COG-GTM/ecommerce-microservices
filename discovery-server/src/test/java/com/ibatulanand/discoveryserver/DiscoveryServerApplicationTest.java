package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscoveryServerApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void main_runsSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(DiscoveryServerApplication.class, new String[]{}))
                    .thenReturn(null);
            DiscoveryServerApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(DiscoveryServerApplication.class, new String[]{}));
        }
    }
}
