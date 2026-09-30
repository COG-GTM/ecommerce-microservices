package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "EUREKA_USERNAME=registry-user",
        "EUREKA_PASSWORD=registry-secret",
        "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String ATTACKER_INSTANCE = """
            {"instance": {
              "instanceId": "attacker.tld:order-service:443",
              "hostName": "attacker.tld",
              "app": "ORDER-SERVICE",
              "ipAddr": "203.0.113.10",
              "status": "UP",
              "port": {"$": 443, "@enabled": "true"},
              "dataCenterInfo": {
                "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                "name": "MyOwn"
              }
            }}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/eureka/apps/ORDER-SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ATTACKER_INSTANCE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDeregistrationIsRejected() throws Exception {
        mockMvc.perform(delete("/eureka/apps/ORDER-SERVICE/order-service:8081"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRegistryReadIsRejected() throws Exception {
        mockMvc.perform(get("/eureka/apps").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDashboardIsRejected() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/eureka/apps/ORDER-SERVICE")
                        .with(httpBasic("registry-user", "wrong"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ATTACKER_INSTANCE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedClientCanReadRegistry() throws Exception {
        mockMvc.perform(get("/eureka/apps")
                        .with(httpBasic("registry-user", "registry-secret"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedClientCanRegisterWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/eureka/apps/ORDER-SERVICE")
                        .with(httpBasic("registry-user", "registry-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ATTACKER_INSTANCE))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void missingCredentialsFailStartup() {
        SecurityConfig config = new SecurityConfig();
        assertThatThrownBy(() -> config.registryUsers("", "", config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.registryUsers("registry-user", " ", config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class);
    }
}
