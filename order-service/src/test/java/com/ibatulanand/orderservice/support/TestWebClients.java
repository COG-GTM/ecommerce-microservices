package com.ibatulanand.orderservice.support;

import okhttp3.mockwebserver.MockWebServer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Redirects the absolute {@code http://inventory-service} URI used by production code to a local
 * {@link MockWebServer}, keeping path and query parameters intact.
 */
public final class TestWebClients {

    private TestWebClients() {
    }

    public static WebClient.Builder redirectingTo(MockWebServer server) {
        return WebClient.builder().filter((request, next) -> next.exchange(
                ClientRequest.from(request)
                        .url(UriComponentsBuilder.fromUri(request.url())
                                .scheme("http")
                                .host(server.getHostName())
                                .port(server.getPort())
                                .build(true)
                                .toUri())
                        .build()));
    }
}
