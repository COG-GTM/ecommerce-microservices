package com.ibatulanand.orderservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderValidationExceptionHandler {

    private static final int MAX_REPORTED_FIELDS = 20;

    /** Reports only field names, never rejected values, and does not log the request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidOrder(MethodArgumentNotValidException exception) {
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .limit(MAX_REPORTED_FIELDS)
                .toList();
        return Map.of("error", "Invalid order request", "fields", fields);
    }

    /** Missing or malformed bodies get the same envelope, without the parser's message. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleUnreadableOrder(HttpMessageNotReadableException exception) {
        return Map.of("error", "Invalid order request", "fields", List.of());
    }
}
