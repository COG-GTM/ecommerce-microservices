package com.ibatulanand.inventoryservice.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<ProblemDetail> skuNotFound(SkuNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "sku-not-found", "SKU not found", ex.getMessage());
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<ProblemDetail> storeNotFound(StoreNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "store-not-found", "Store not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> validationFailed(IllegalArgumentException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed", "Validation failed", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.addAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(body, problemHeaders, statusCode);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
