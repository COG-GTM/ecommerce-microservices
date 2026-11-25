package com.ibatulanand.orderservice.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Refund rejected; rendered as {"error": code, "message": message}. */
@Getter
public class RefundException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RefundException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
