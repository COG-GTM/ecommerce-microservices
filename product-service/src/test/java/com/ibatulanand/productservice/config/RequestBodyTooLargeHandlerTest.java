package com.ibatulanand.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyTooLargeHandlerTest {

    private final RequestBodyTooLargeHandler handler = new RequestBodyTooLargeHandler();

    @Test
    void returns413WhenBodyWasCutOff() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handleUnreadableBody(unreadable(new RequestBodyTooLargeException(1024)), response);

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void returns400ForOtherUnreadableBodies() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handleUnreadableBody(unreadable(new java.io.IOException("bad json")), response);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    private static HttpMessageNotReadableException unreadable(Exception cause) {
        return new HttpMessageNotReadableException("I/O error while reading input message", cause,
                new MockHttpInputMessage(new byte[0]));
    }
}
