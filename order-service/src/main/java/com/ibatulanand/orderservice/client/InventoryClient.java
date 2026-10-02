package com.ibatulanand.orderservice.client;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.exception.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class InventoryClient {

    private final WebClient.Builder webClientBuilder;

    @CircuitBreaker(name = "inventory", fallbackMethod = "checkStockFallback")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<List<InventoryResponse>> checkStock(List<String> skuCodes) {
        return CompletableFuture.supplyAsync(() -> {
            InventoryResponse[] responses = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();
            return Arrays.asList(responses == null ? new InventoryResponse[0] : responses);
        });
    }

    private CompletableFuture<List<InventoryResponse>> checkStockFallback(List<String> skuCodes, Throwable throwable) {
        throw new UpstreamUnavailableException("Inventory service unavailable, please try again later", throwable);
    }
}
