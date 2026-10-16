package com.ibatulanand.notificationservice.listener;

/** An event that can never be processed; it goes straight to the DLT without retries. */
public class InvalidOrderEventException extends RuntimeException {

    public InvalidOrderEventException(String message) {
        super(message);
    }
}
