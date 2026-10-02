package com.ibatulanand.orderservice.exception;

public class TaxExemptNotAuthorizedException extends RuntimeException {
    public TaxExemptNotAuthorizedException(String message) {
        super(message);
    }
}
