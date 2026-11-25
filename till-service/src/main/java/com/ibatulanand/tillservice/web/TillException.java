package com.ibatulanand.tillservice.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Business error rendered as {"error": code, "message": message}. */
@Getter
public class TillException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TillException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
