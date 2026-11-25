package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import com.ibatulanand.orderservice.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    // Spring's task executor keeps the application classloader (ForkJoin common pool threads in a fat jar do not).
    private final Executor executor;

    public OrderController(OrderService orderService, @Qualifier("applicationTaskExecutor") Executor executor) {
        this.orderService = orderService;
        this.executor = executor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        // Idempotency keys belong to till sales only, so an online order can never claim a till basket's key.
        orderRequest.setIdempotencyKey(null);
        orderRequest.setTenders(null);
        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest), executor);
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        return CompletableFuture.supplyAsync(() -> "Oops! Something went wrong, please order after some time!", executor);
    }

    /** Till sale: synchronous, returns the persisted order so the till can print the order number on the receipt. */
    @PostMapping("/sale")
    public ResponseEntity<?> placeSale(@RequestBody OrderRequest orderRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(OrderResponse.from(orderService.placeAndReturnOrder(orderRequest)));
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate of this sale won the unique idempotency key: replay its order.
            return orderService.findByIdempotencyKey(orderRequest.getIdempotencyKey())
                    .<ResponseEntity<?>>map(order -> ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order)))
                    .orElseThrow(() -> e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "OUT_OF_STOCK", "message", e.getMessage()));
        }
    }

    /** Every order recorded for a till transaction; a correctly retried tender yields exactly one. */
    @GetMapping("/transaction/{transactionId}")
    public List<OrderResponse> getOrdersForTransaction(@PathVariable String transactionId) {
        return orderService.findByTransactionId(transactionId).stream().map(OrderResponse::from).toList();
    }

    /** Till orders in the order they were stored, e.g. to reconcile a till's synced offline sales. */
    @GetMapping(params = "tillId")
    public List<OrderResponse> ordersForTill(@RequestParam String tillId) {
        return orderService.findByTillId(tillId).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
        return orderService.findByOrderNumber(orderNumber)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
