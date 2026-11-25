package com.ibatulanand.productservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Rejects request bodies larger than {@code product.request.max-body-bytes} with 413. Bodies without a
 * Content-Length are counted as they are read, so nothing is read before the handler asks for it.
 * Runs before {@link OrderedFormContentFilter} so form parsing is limited too.
 */
@Component
@Order(OrderedFormContentFilter.DEFAULT_ORDER - 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    public static final int DEFAULT_MAX_BODY_BYTES = 32 * 1024;

    private final int maxBodyBytes;

    public RequestBodyLimitFilter(@Value("${product.request.max-body-bytes:" + DEFAULT_MAX_BODY_BYTES + "}") int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBodyBytes) {
            reject(response);
            return;
        }
        if (declaredLength >= 0) {
            chain.doFilter(request, response);
            return;
        }
        try {
            chain.doFilter(new LimitedBodyRequest(request, maxBodyBytes), response);
        } catch (RequestBodyTooLargeException e) {
            if (response.isCommitted()) {
                throw e;
            }
            response.reset();
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Request body exceeds " + maxBodyBytes + " bytes");
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final int maxBodyBytes;
        private ServletInputStream limitedStream;

        LimitedBodyRequest(HttpServletRequest request, int maxBodyBytes) {
            super(request);
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedStream == null) {
                limitedStream = new LimitedServletInputStream(super.getInputStream(), maxBodyBytes);
            }
            return limitedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int maxBodyBytes;
        private long bytesRead;

        LimitedServletInputStream(ServletInputStream delegate, int maxBodyBytes) {
            this.delegate = delegate;
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws RequestBodyTooLargeException {
            bytesRead += n;
            if (bytesRead > maxBodyBytes) {
                throw new RequestBodyTooLargeException(maxBodyBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
