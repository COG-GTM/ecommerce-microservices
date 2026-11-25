package com.ibatulanand.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "EUREKA_USERNAME=registry-user",
        "EUREKA_PASSWORD=registry-secret",
        "management.tracing.enabled=false",
        "eureka.server.use-read-only-response-cache=false"
})
class SecurityConfigTest {

    private static final String ATTACKER_INSTANCE = """
            {"instance": {
              "instanceId": "attacker.tld:product-service:443",
              "hostName": "attacker.tld",
              "app": "PRODUCT-SERVICE",
              "ipAddr": "203.0.113.10",
              "status": "UP",
              "port": {"$": 443, "@enabled": "true"},
              "dataCenterInfo": {
                "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                "name": "MyOwn"
              }
            }}
            """;

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        assertThat(send(register(), null).statusCode()).isEqualTo(401);
        assertThat(send(listApps(), basic("registry-user", "registry-secret")).body())
                .doesNotContain("attacker.tld");
    }

    @Test
    void anonymousStatusChangeAndDeregistrationAreRejected() throws Exception {
        HttpRequest.Builder outOfService = request("/eureka/apps/PRODUCT-SERVICE/product-service:8080/status?value=OUT_OF_SERVICE")
                .PUT(HttpRequest.BodyPublishers.noBody());
        HttpRequest.Builder delete = request("/eureka/apps/PRODUCT-SERVICE/product-service:8080").DELETE();
        assertThat(send(outOfService, null).statusCode()).isEqualTo(401);
        assertThat(send(delete, null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousRegistryReadIsRejected() throws Exception {
        assertThat(send(listApps(), null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousDashboardIsRejected() throws Exception {
        assertThat(send(request("/").GET(), null).statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        assertThat(send(register(), basic("registry-user", "wrong")).statusCode()).isEqualTo(401);
    }

    @Test
    void authenticatedClientCanRegisterWithoutCsrfTokenAndIsListed() throws Exception {
        String credentials = basic("registry-user", "registry-secret");
        assertThat(send(register(), credentials).statusCode()).isEqualTo(204);

        HttpResponse<String> apps = send(listApps(), credentials);
        assertThat(apps.statusCode()).isEqualTo(200);
        assertThat(apps.body()).contains("attacker.tld");
        assertThat(apps.headers().firstValue("Set-Cookie")).isEmpty();

        HttpResponse<String> deregister = send(request("/eureka/apps/PRODUCT-SERVICE/attacker.tld:product-service:443").DELETE(), credentials);
        assertThat(deregister.statusCode()).isEqualTo(200);
    }

    @Test
    void missingCredentialsFailStartup() {
        SecurityConfig config = new SecurityConfig();
        assertThatThrownBy(() -> config.registryUsers("", "", config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.registryUsers("registry-user", " ", config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class);
    }

    private HttpRequest.Builder register() {
        return request("/eureka/apps/PRODUCT-SERVICE")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ATTACKER_INSTANCE));
    }

    private HttpRequest.Builder listApps() {
        return request("/eureka/apps").header("Accept", "application/json").GET();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String authorization) throws Exception {
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
