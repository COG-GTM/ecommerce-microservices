package com.ibatulanand.productservice.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * Turns a body that {@link RequestBodyLimitFilter} cut off while it was being parsed into 413 instead of 400.
 */
@RestControllerAdvice
public class RequestBodyTooLargeHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletResponse response) throws IOException {
        if (ex.getMostSpecificCause() instanceof RequestBodyTooLargeException tooLarge) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), tooLarge.getMessage());
        } else {
            response.sendError(HttpStatus.BAD_REQUEST.value());
        }
    }
}
