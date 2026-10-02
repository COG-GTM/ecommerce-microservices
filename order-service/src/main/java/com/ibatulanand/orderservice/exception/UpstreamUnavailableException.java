package com.ibatulanand.orderservice.exception;

public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String message) {
        super(message);
    }

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
