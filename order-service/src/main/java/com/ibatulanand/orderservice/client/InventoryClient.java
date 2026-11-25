package com.ibatulanand.orderservice.client;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.exception.InventoryUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * The only call guarded by the "inventory" circuit breaker, so that only inventory-service
 * faults (errors, timeouts) can open it.
 */
@Component
public class InventoryClient {

    private final WebClient.Builder webClientBuilder;
    private final Duration timeout;

    public InventoryClient(WebClient.Builder webClientBuilder,
                           @Value("${inventory.client.timeout:3s}") Duration timeout) {
        this.webClientBuilder = webClientBuilder;
        this.timeout = timeout;
    }

    @CircuitBreaker(name = "inventory")
    @Retry(name = "inventory", fallbackMethod = "inventoryUnavailable")
    public List<InventoryResponse> getStock(List<String> skuCodes) {
        InventoryResponse[] inventoryResponses = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block(timeout);
        return inventoryResponses == null ? List.of() : List.of(inventoryResponses);
    }

    private List<InventoryResponse> inventoryUnavailable(List<String> skuCodes, Throwable throwable) {
        throw new InventoryUnavailableException(throwable);
    }
}
