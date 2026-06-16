package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void securityFilterChainBeanExists() {
        // Building the SecurityFilterChain bean exercises SecurityConfig, including
        // the CSRF customizer that ignores the /eureka/** endpoints.
        assertThat(securityFilterChain).isNotNull();
    }
}
