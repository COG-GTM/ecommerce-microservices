package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.AgeRestrictedItemException;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.model.Order;
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
        // Till identifiers belong to till sales only, so an online order can never claim a till basket's key
        // or appear in a till transaction lookup.
        orderRequest.setIdempotencyKey(null);
        orderRequest.setTenders(null);
        orderRequest.setTillId(null);
        orderRequest.setTransactionId(null);
        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest), executor);
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        // A rejected order is the caller's mistake, not a downstream outage: surface it instead of the generic fallback.
        for (Throwable t = runtimeException; t != null; t = t.getCause()) {
            if (t instanceof AgeRestrictedItemException restricted) {
                return CompletableFuture.failedFuture(restricted);
            }
        }
        return CompletableFuture.supplyAsync(() -> "Oops! Something went wrong, please order after some time!", executor);
    }

    @ExceptionHandler(AgeRestrictedItemException.class)
    public ResponseEntity<Map<String, Object>> ageRestricted(AgeRestrictedItemException e) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "AGE_RESTRICTED_ITEM", "message", e.getMessage(), "skuCodes", e.getSkuCodes()));
    }

    /** Till sale: synchronous, returns the persisted order so the till can print the order number on the receipt. */
    @PostMapping("/sale")
    public ResponseEntity<?> placeSale(@RequestBody OrderRequest orderRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(OrderResponse.from(orderService.placeAndReturnOrder(orderRequest)));
        } catch (OrderService.IdempotencyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "IDEMPOTENCY_CONFLICT", "message", e.getMessage()));
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

    static final int MAX_TILL_PAGE = 500;
    static final String NEXT_PAGE_HEADER = "X-Next-After-Id";

    /**
     * Till orders in the order they were stored, e.g. to reconcile a till's synced offline sales. Paged by id: when a
     * page is full, {@value #NEXT_PAGE_HEADER} holds the {@code afterId} that fetches the next one.
     */
    @GetMapping(params = "tillId")
    public ResponseEntity<List<OrderResponse>> ordersForTill(@RequestParam String tillId,
                                                             @RequestParam(defaultValue = "0") long afterId,
                                                             @RequestParam(defaultValue = "200") int limit) {
        int pageSize = Math.max(1, Math.min(limit, MAX_TILL_PAGE));
        List<Order> page = orderService.findByTillId(tillId, afterId, pageSize);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (page.size() == pageSize) {
            response.header(NEXT_PAGE_HEADER, String.valueOf(page.get(page.size() - 1).getId()));
        }
        return response.body(page.stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
        return orderService.findByOrderNumber(orderNumber)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
