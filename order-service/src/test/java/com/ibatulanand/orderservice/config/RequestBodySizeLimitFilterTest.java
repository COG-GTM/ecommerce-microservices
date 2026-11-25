package com.ibatulanand.orderservice.config;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodySizeLimitFilterTest {

    private static final int LIMIT = 100 * 1024;

    private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(DataSize.ofKilobytes(100));

    @Test
    void rejectsDeclaredContentLengthAboveLimit() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT + 1], true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesBodyAtLimit() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT], true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsChunkedBodyAboveLimit() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT + 1], false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void replaysChunkedBodyWithinLimit() throws Exception {
        byte[] body = new byte[LIMIT];
        Arrays.fill(body, (byte) 'a');
        MockHttpServletRequest request = post(body, false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ServletRequest forwarded = chain.getRequest();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(forwarded.getContentLengthLong()).isEqualTo(LIMIT);
        assertThat(forwarded.getInputStream().readAllBytes()).isEqualTo(body);
    }

    @Test
    void rejectsChunkedFormBodyAboveLimitOnDelete() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT + 1], false);
        request.setMethod("DELETE");
        request.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void runsBeforeFormContentParsing() {
        assertThat(OrderUtils.getOrder(RequestBodySizeLimitFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(Ordered.HIGHEST_PRECEDENCE).isLessThan(new OrderedFormContentFilter().getOrder());
    }

    @Test
    void ignoresRequestsWithoutBodyMethods() throws Exception {
        MockHttpServletRequest request = post(new byte[LIMIT + 1], true);
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest post(byte[] body, boolean withContentLength) {
        MockHttpServletRequest request = withContentLength
                ? new MockHttpServletRequest()
                : new MockHttpServletRequest() {
                    @Override
                    public int getContentLength() {
                        return -1;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }
                };
        request.setMethod("POST");
        request.setRequestURI("/api/order");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
