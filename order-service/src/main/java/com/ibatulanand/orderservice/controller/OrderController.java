package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<ResponseEntity<OrderResponse>> placeOrder(@RequestBody OrderRequest orderRequest,
                                                                       UriComponentsBuilder uriComponentsBuilder) {
        return CompletableFuture.supplyAsync(() -> {
            OrderResponse orderResponse = orderService.placeOrder(orderRequest);
            URI location = uriComponentsBuilder.path("/api/order/{orderNumber}")
                    .buildAndExpand(orderResponse.getOrderNumber())
                    .toUri();
            return ResponseEntity.created(location).body(orderResponse);
        });
    }

    public CompletableFuture<ResponseEntity<OrderResponse>> fallbackMethod(OrderRequest orderRequest,
                                                                           UriComponentsBuilder uriComponentsBuilder,
                                                                           RuntimeException runtimeException) {
        return CompletableFuture.supplyAsync(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
        return orderService.getOrder(orderNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
