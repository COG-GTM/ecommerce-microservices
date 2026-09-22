package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest));
    }

    @PostMapping("/quote")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse quoteOrder(@RequestBody OrderRequest orderRequest) {
        return orderService.priceOrder(orderRequest);
    }

    public CompletableFuture<OrderResponse> fallbackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Oops! Something went wrong, please order after some time!", runtimeException);
    }
}
