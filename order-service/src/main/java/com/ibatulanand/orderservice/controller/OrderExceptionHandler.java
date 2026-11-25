package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.exception.InvalidOrderException;
import com.ibatulanand.orderservice.exception.InventoryUnavailableException;
import com.ibatulanand.orderservice.exception.OutOfStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail invalidOrder(InvalidOrderException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    public ProblemDetail outOfStock(OutOfStockException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("skuCodes", exception.getSkuCodes());
        return problem;
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    public ProblemDetail inventoryUnavailable(InventoryUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
