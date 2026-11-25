package com.ibatulanand.productservice.pricing;

import org.springframework.http.HttpStatus;

/** A rejected price operation; rendered as {@code {"error": code, "message": ...}}. */
public class PricingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PricingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
