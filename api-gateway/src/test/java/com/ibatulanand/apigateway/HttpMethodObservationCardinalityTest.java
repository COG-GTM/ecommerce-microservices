package com.ibatulanand.apigateway;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CVE-2023-34053 / CVE-2023-34055: made-up HTTP methods must not each create a new
 * {@code http.server.requests} series on the WebFlux gateway.
 */
class HttpMethodObservationCardinalityTest {

    private final DefaultServerRequestObservationConvention convention = new DefaultServerRequestObservationConvention();

    @Test
    void madeUpHttpMethodsShareOneMethodTag() {
        Set<String> methodTags = IntStream.range(0, 50)
                .mapToObj(i -> methodTag(HttpMethod.valueOf("MADEUP" + i)))
                .collect(Collectors.toSet());

        assertThat(methodTags).containsExactly("UNKNOWN");
    }

    @Test
    void standardHttpMethodsKeepTheirName() {
        assertThat(methodTag(HttpMethod.GET)).isEqualTo("GET");
        assertThat(methodTag(HttpMethod.POST)).isEqualTo("POST");
    }

    private String methodTag(HttpMethod method) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method, "/api/product").build();
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, new MockServerHttpResponse(), Map.of());
        return convention.getLowCardinalityKeyValues(context).stream()
                .filter(keyValue -> keyValue.getKey().equals("method"))
                .map(KeyValue::getValue)
                .findFirst()
                .orElseThrow();
    }
}
