package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"EUREKA_USERNAME=eureka-test", "EUREKA_PASSWORD=test-secret"})
class DiscoveryServerSecurityTests {

    private static final String INSTANCE_JSON = """
            {"instance":{"instanceId":"attacker","hostName":"attacker.tld","app":"ORDER-SERVICE",
            "ipAddr":"10.0.0.1","status":"UP","port":{"$":80,"@enabled":"true"},
            "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anonymousRegistryReadIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity("/eureka/apps", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void anonymousRegistrationIsRejected() {
        ResponseEntity<String> response = register(restTemplate);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void anonymousDeregistrationIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange("/eureka/apps/ORDER-SERVICE/attacker",
                HttpMethod.DELETE, null, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void wrongCredentialsAreRejected() {
        ResponseEntity<String> response = restTemplate.withBasicAuth("eureka-test", "wrong")
                .getForEntity("/eureka/apps", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void anonymousDashboardIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void authenticatedClientCanRegisterAndReadRegistry() {
        TestRestTemplate authenticated = restTemplate.withBasicAuth("eureka-test", "test-secret");
        assertEquals(HttpStatus.NO_CONTENT, register(authenticated).getStatusCode());
        assertEquals(HttpStatus.OK, authenticated.getForEntity("/eureka/apps", String.class).getStatusCode());
    }

    @Test
    void startupFailsWithoutCredentials() {
        assertThrows(Exception.class, () -> new SpringApplicationBuilder(DiscoveryServerApplication.class)
                .properties("server.port=0", "EUREKA_USERNAME=eureka-test")
                .run()
                .close());
    }

    private static ResponseEntity<String> register(TestRestTemplate template) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return template.postForEntity("/eureka/apps/ORDER-SERVICE", new HttpEntity<>(INSTANCE_JSON, headers),
                String.class);
    }
}
