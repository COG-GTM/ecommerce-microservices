package com.ibatulanand.orderservice.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Maps to an {@code application/problem+json} response with a stable {@code type} slug (PRE-1).
 * {@link Rejected} is a business outcome and is ignored by the circuit breakers and retry;
 * {@link DependencyUnavailable} is what the fallbacks raise.
 */
@Getter
public abstract class OrderProblemException extends RuntimeException {
    public static final String TYPE_PREFIX = "urn:problem-type:gap-pos:";

    private final HttpStatus status;
    private final String typeSlug;
    private final String title;

    protected OrderProblemException(HttpStatus status, String typeSlug, String title, String detail) {
        super(detail);
        this.status = status;
        this.typeSlug = typeSlug;
        this.title = title;
    }

    public static Rejected outOfStock(String detail) {
        return new Rejected(HttpStatus.CONFLICT, "out-of-stock", "Out of stock", detail);
    }

    public static Rejected invalidPromotion(String detail) {
        return new Rejected(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-promotion", "Invalid promotion", detail);
    }

    public static Rejected validationFailed(String detail) {
        return new Rejected(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed", "Validation failed", detail);
    }

    public static DependencyUnavailable dependencyUnavailable(String detail) {
        return new DependencyUnavailable(detail);
    }

    public static class Rejected extends OrderProblemException {
        Rejected(HttpStatus status, String typeSlug, String title, String detail) {
            super(status, typeSlug, title, detail);
        }
    }

    public static class DependencyUnavailable extends OrderProblemException {
        DependencyUnavailable(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable", "Dependency unavailable", detail);
        }
    }
}
