package com.ibatulanand.orderservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientConfigTest {

    @Test
    void providesFreshWebClientBuilder() {
        WebClientConfig config = new WebClientConfig();

        assertThat(config.webClientBuilder()).isNotNull().isNotSameAs(config.webClientBuilder());
    }
}
