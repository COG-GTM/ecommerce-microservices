package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the Eureka server and verifies the security chain: CSRF is ignored for /eureka/** so
 * clients can register, while state-changing requests elsewhere still require a CSRF token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.enabled=false")
class SecurityConfigTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void clientCanRegisterWithoutCsrfToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String instance = """
                {"instance":{"instanceId":"svc-1","hostName":"localhost","app":"TEST-SERVICE","ipAddr":"127.0.0.1",
                "status":"UP","port":{"$":8080,"@enabled":"true"},
                "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
                """;

        ResponseEntity<String> registration = rest.postForEntity("/eureka/apps/TEST-SERVICE", new HttpEntity<>(instance, headers), String.class);

        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        HttpHeaders accept = new HttpHeaders();
        accept.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> apps = rest.exchange("/eureka/apps/TEST-SERVICE", org.springframework.http.HttpMethod.GET, new HttpEntity<>(accept), String.class);
        assertThat(apps.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apps.getBody()).contains("svc-1");
    }

    @Test
    void postOutsideEurekaPathsRequiresCsrfToken() {
        ResponseEntity<String> response = rest.postForEntity("/not-eureka", "{}", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
