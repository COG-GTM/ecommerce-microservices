package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.exception.OutOfStockException;
import com.ibatulanand.orderservice.service.OrderService;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderResponse(String orderNumber, String status, String message, List<String> skuCodes) {}

    @PostMapping
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<ResponseEntity<OrderResponse>> placeOrder(@RequestBody OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String orderNumber = orderService.placeOrder(orderRequest);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new OrderResponse(orderNumber, "CREATED", "Order placed successfully", null));
            } catch (OutOfStockException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new OrderResponse(null, "OUT_OF_STOCK", e.getMessage(), e.getSkuCodes()));
            }
        });
    }

    public CompletableFuture<ResponseEntity<OrderResponse>> fallbackMethod(OrderRequest orderRequest, Throwable throwable) {
        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new OrderResponse(null, "SERVICE_UNAVAILABLE",
                        "Inventory service is unavailable, please try again later", null)));
    }
}
