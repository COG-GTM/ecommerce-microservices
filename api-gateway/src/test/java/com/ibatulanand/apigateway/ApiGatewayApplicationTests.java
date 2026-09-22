package com.ibatulanand.apigateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@SpringBootTest
class ApiGatewayApplicationTests {

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(applicationContext);
    }

    @Test
    void securityWebFilterChainBeanIsRegistered() {
        Assertions.assertNotNull(applicationContext.getBean(SecurityWebFilterChain.class));
    }
}
