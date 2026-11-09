package com.ibatulanand.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesIdWhenHeaderMissingAndExposesItInMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIds.MDC_KEY)));

        String echoed = response.getHeader(CorrelationIds.HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(UUID.fromString(echoed)).isNotNull();
        assertThat(mdcDuringChain.get()).isEqualTo(echoed);
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void reusesIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product");
        request.addHeader(CorrelationIds.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIds.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("abc-123");
        assertThat(mdcDuringChain.get()).isEqualTo("abc-123");
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product");
        request.addHeader(CorrelationIds.HEADER, "bad\r\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String echoed = response.getHeader(CorrelationIds.HEADER);
        assertThat(echoed).isNotEqualTo("bad\r\nvalue");
        assertThat(UUID.fromString(echoed)).isNotNull();
    }

    @Test
    void echoesHeaderAndClearsMdcWhenChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product");
        request.addHeader(CorrelationIds.HEADER, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isInstanceOf(ServletException.class);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("req-1");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }
}
