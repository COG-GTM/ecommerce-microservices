package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
public class InventoryClient {

    private final WebClient.Builder webClientBuilder;
    private final Duration responseTimeout;

    public InventoryClient(WebClient.Builder webClientBuilder,
                           @Value("${inventory.client.response-timeout:800ms}") Duration responseTimeout) {
        this.webClientBuilder = webClientBuilder;
        this.responseTimeout = responseTimeout;
    }

    // Read-only lookup, so it is the only step that is safe to retry.
    @Retry(name = "inventory")
    public InventoryResponse[] getStock(List<String> skuCodes) {
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .timeout(responseTimeout)
                .block();
        if (inventoryResponseArray == null) {
            throw new IllegalStateException("Inventory service returned no stock information");
        }
        return inventoryResponseArray;
    }
}
