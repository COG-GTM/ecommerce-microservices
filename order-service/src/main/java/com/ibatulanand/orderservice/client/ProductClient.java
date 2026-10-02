package com.ibatulanand.orderservice.client;

import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.exception.UpstreamUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient.Builder webClientBuilder;

    public Optional<ProductResponse> findBySku(String skuCode) {
        try {
            return webClientBuilder.build().get()
                    .uri("http://product-service/api/product/sku/{skuCode}", skuCode)
                    .retrieve()
                    .bodyToMono(ProductResponse.class)
                    .timeout(TIMEOUT)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .onErrorResume(WebClientResponseException.NotFound.class,
                            e -> Mono.just(Optional.empty()))
                    .onErrorMap(UpstreamUnavailableException.class, e -> e)
                    .onErrorMap(e -> new UpstreamUnavailableException(
                            "Product service unavailable", e))
                    .block();
        } catch (UpstreamUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UpstreamUnavailableException("Product service unavailable", e);
        }
    }
}
