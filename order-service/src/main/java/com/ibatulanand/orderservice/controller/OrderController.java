package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // No @Retry/@TimeLimiter here: placing an order is not safe to repeat, and a time limiter
    // does not stop the running task. Retries and timeouts apply to the inventory lookup only.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest, idempotencyKey));
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, String idempotencyKey, RuntimeException runtimeException) {
        Throwable cause = runtimeException instanceof CompletionException && runtimeException.getCause() != null
                ? runtimeException.getCause() : runtimeException;
        if (cause instanceof ResponseStatusException responseStatusException) {
            return CompletableFuture.failedFuture(responseStatusException);
        }
        return CompletableFuture.supplyAsync(() -> "Oops! Something went wrong, please order after some time!");
    }
}
