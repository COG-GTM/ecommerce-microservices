package com.ibatulanand.productservice.config;

import java.io.IOException;

public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(int maxBodyBytes) {
        super("Request body exceeds " + maxBodyBytes + " bytes");
    }
}
