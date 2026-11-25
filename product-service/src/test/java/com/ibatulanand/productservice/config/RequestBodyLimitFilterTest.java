package com.ibatulanand.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.FormContentFilter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyLimitFilterTest {

    private static final int LIMIT = 1024;

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(LIMIT);

    @Test
    void rejectsDeclaredLengthOverLimit() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesBodyAtLimit() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsChunkedBodyOverLimitWhenRead() throws Exception {
        MockHttpServletRequest request = chunked(new ByteArrayInputStream(new byte[1024 * 1024]));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger bytesRead = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> {
            InputStream in = req.getInputStream();
            byte[] buffer = new byte[256];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bytesRead.addAndGet(n);
            }
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(bytesRead.get()).isLessThanOrEqualTo(LIMIT);
    }

    @Test
    void doesNotReadChunkedBodyTheHandlerIgnores() throws Exception {
        ByteArrayInputStream body = new ByteArrayInputStream(new byte[1024 * 1024]);
        MockHttpServletRequest request = chunked(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(body.available()).isEqualTo(1024 * 1024);
    }

    @Test
    void passesChunkedBodyWithinLimit() throws Exception {
        byte[] body = "{\"name\":\"Iphone 15\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = chunked(new ByteArrayInputStream(body));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest().getInputStream().readAllBytes()).isEqualTo(body);
    }

    @Test
    void limitsFormParsingOfChunkedBodies() throws Exception {
        String form = "a=" + "x".repeat(1024 * 1024);
        MockHttpServletRequest request = chunked(new ByteArrayInputStream(form.getBytes(StandardCharsets.UTF_8)));
        request.setMethod("PUT");
        request.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, (req, res) ->
                new FormContentFilter().doFilter(req, res, chain));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletRequest post(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/product");
        request.setContent(body);
        return request;
    }

    private static MockHttpServletRequest chunked(InputStream body) {
        return new MockHttpServletRequest("POST", "/api/product") {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                return new org.springframework.mock.web.DelegatingServletInputStream(body);
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
    }
}
