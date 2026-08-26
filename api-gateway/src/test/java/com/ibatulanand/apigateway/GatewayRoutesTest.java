package com.ibatulanand.apigateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import org.eclipse.microprofile.config.ConfigProvider;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
@QuarkusTestResource(OpenTelemetryTestResource.class)
@QuarkusTestResource(io.quarkus.test.keycloak.server.KeycloakTestResourceLifecycleManager.class)
class GatewayRoutesTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @BeforeEach
    void resetWireMock() {
        WireMockTestResource.server().resetAll();
        OpenTelemetryTestResource.reset();
    }

    @Test
    void protectsServiceRoutesWithBearerAuthentication() {
        given().get("/api/product").then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
        given().get("/api/order").then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
    }

    @Test
    void permitsHealthMetricsAndEurekaWithoutRedirecting() {
        given().get("/q/health").then().statusCode(200);
        given().get("/q/metrics").then().statusCode(200);
        given().get("/eureka/web").then().statusCode(not(401)).statusCode(not(302));
    }

    @Test
    void proxiesGetResponseStatusBodyAndContentType() {
        WireMockTestResource.server().stubFor(get(urlEqualTo("/api/product"))
                .willReturn(WireMock.aResponse().withStatus(418)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("product response")));

        given().auth().oauth2(keycloak.getClientAccessToken())
                .get("/api/product")
                .then().statusCode(418)
                .contentType("text/plain")
                .body(containsString("product response"));
    }

    @Test
    void forwardsPostMethodAndJsonBody() {
        String body = "{\"productId\":\"p-1\",\"quantity\":2}";
        WireMockTestResource.server().stubFor(post(urlEqualTo("/api/order"))
                .willReturn(WireMock.aResponse().withStatus(201).withBody("created")));

        given().auth().oauth2(keycloak.getClientAccessToken())
                .contentType("application/json").body(body)
                .post("/api/order").then().statusCode(201);

        WireMockTestResource.server().verify(
                postRequestedFor(urlEqualTo("/api/order")).withRequestBody(equalToJson(body)));
    }

    @Test
    void preservesRepeatedRequestAndResponseHeaders() {
        WireMockTestResource.server().stubFor(get(urlEqualTo("/api/product"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Set-Cookie", "first=1")
                        .withHeader("Set-Cookie", "second=2")
                        .withBody("headers")));

        Response response = given().auth().oauth2(keycloak.getClientAccessToken())
                .header("X-Repeated", "first")
                .header("X-Repeated", "second")
                .get("/api/product");

        assertEquals(200, response.statusCode());
        assertEquals(List.of("first", "second"), WireMockTestResource.server().getAllServeEvents().get(0)
                .getRequest().getHeaders().getHeader("X-Repeated").values());
        assertEquals(List.of("first=1", "second=2"), response.getHeaders().getValues("Set-Cookie"));
    }

    @Test
    void passesUpstreamErrorsThrough() {
        WireMockTestResource.server().stubFor(get(urlEqualTo("/api/product"))
                .willReturn(WireMock.aResponse().withStatus(404)));
        given().auth().oauth2(keycloak.getClientAccessToken())
                .get("/api/product").then().statusCode(404);

        WireMockTestResource.server().resetAll();
        WireMockTestResource.server().stubFor(get(urlEqualTo("/api/product"))
                .willReturn(WireMock.aResponse().withStatus(503)));
        given().auth().oauth2(keycloak.getClientAccessToken())
                .get("/api/product").then().statusCode(503);
    }

    @Test
    void proxiesEurekaPathsToConsulUi() {
        WireMockTestResource.server().stubFor(get(urlEqualTo("/ui/"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("consul ui")));
        given().get("/eureka/web").then().statusCode(200).body(containsString("consul ui"));
    }

    @Test
    void exportsServerSpanForHealthRoute() {
        given().get("/q/health").then().statusCode(200);
        flushOpenTelemetry();
        List<SpanData> spans = OpenTelemetryTestResource.exporter().getFinishedSpanItems();
        assertTrue(OpenTelemetryTestResource.exporter().getFinishedSpanItems().stream()
                .anyMatch(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER),
                () -> "Exported spans: " + spans);
    }

    @Test
    void usesTheStaticTestServicesAndConsulProductionDeclarations() throws IOException {
        String production = Files.readString(Path.of("src/main/resources/application.properties"));
        assertTrue(production.contains("quarkus.stork.product-service.service-discovery.type=consul"));
        assertTrue(production.contains("quarkus.stork.order-service.service-discovery.type=consul"));
        assertEquals("static", ConfigProvider.getConfig().getValue(
                "quarkus.stork.product-service.service-discovery.type", String.class));
        assertEquals("static", ConfigProvider.getConfig().getValue(
                "quarkus.stork.order-service.service-discovery.type", String.class));
    }

    @Test
    void forwardsTraceparentAndCreatesChildClientSpan() {
        WireMockTestResource.server().stubFor(get(urlEqualTo("/api/product"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("traced")));

        given().auth().oauth2(keycloak.getClientAccessToken()).get("/api/product").then().statusCode(200);
        flushOpenTelemetry();

        String traceparent = WireMockTestResource.server().getAllServeEvents().get(0)
                .getRequest().getHeader("traceparent");
        assertNotNull(traceparent);
        assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"));

        List<SpanData> spans = OpenTelemetryTestResource.exporter().getFinishedSpanItems();
        String traceId = traceId(traceparent);
        SpanData gateway = spans.stream()
                .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
                .filter(span -> span.getSpanContext().getTraceId().equals(traceId))
                .findFirst().orElseThrow();
        SpanData client = spans.stream()
                .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT)
                .filter(span -> span.getSpanContext().getTraceId().equals(traceId))
                .findFirst().orElseThrow();
        assertEquals(gateway.getSpanContext().getTraceId(), client.getSpanContext().getTraceId(),
                () -> "Spans: " + spans);
        assertEquals(gateway.getSpanContext().getSpanId(), client.getParentSpanId(),
                () -> "Spans: " + spans);
        assertEquals(gateway.getSpanContext().getTraceId(), traceId,
                () -> "Spans: " + spans);
    }

    private static void flushOpenTelemetry() {
        OpenTelemetry openTelemetry = jakarta.enterprise.inject.spi.CDI.current()
                .select(OpenTelemetry.class).get();
        assertTrue(openTelemetry instanceof OpenTelemetrySdk);
        ((OpenTelemetrySdk) openTelemetry).getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);
    }

    private static String traceId(String traceparent) {
        return traceparent.substring(3, 35);
    }
}
