package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.service.OrderProblemException;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private static final URI ORDER_PATH = URI.create("/api/order");

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> orderService.placeOrder(orderRequest));
    }

    /** Business rejections pass through unchanged; anything else becomes a 503, never a 201. */
    public CompletableFuture<OrderResponse> fallbackMethod(OrderRequest orderRequest, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof OrderProblemException problem) {
            return CompletableFuture.failedFuture(problem);
        }
        log.warn("Order could not be placed: {}", cause.toString());
        return CompletableFuture.failedFuture(OrderProblemException.dependencyUnavailable(
                "The order could not be placed because a downstream service is unavailable. Please try again."));
    }

    @ExceptionHandler(OrderProblemException.class)
    public ResponseEntity<ProblemDetail> handleOrderProblem(OrderProblemException problem) {
        return problem(problem.getStatus(), problem.getTypeSlug(), problem.getTitle(), problem.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed", "Validation failed",
                "Request body could not be read: " + exception.getMostSpecificCause().getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(OrderProblemException.TYPE_PREFIX + slug));
        body.setTitle(title);
        body.setInstance(ORDER_PATH);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
