package com.ibatulanand.productservice.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.config.RequestBodyLimitFilter;
import com.ibatulanand.productservice.config.RequestBodyTooLargeHandler;
import com.ibatulanand.productservice.service.ProductService;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunked JSON bodies through the body limit filter and the Jackson settings from application.properties.
 */
class ChunkedProductBodyLimitTest {

    private static final int LIMIT = 1024;
    private static final String VALID = "{\"name\":\"Iphone 15\",\"description\":\"Apple Iphone 15\",\"price\":1500}";

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new RequestBodyTooLargeHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new RequestBodyLimitFilter(LIMIT))
                .build();
    }

    @Test
    void createsProductFromChunkedBodyWithinLimit() throws Exception {
        postChunked(VALID).andExpect(status().isCreated());
        verify(productService).createProduct(any());
    }

    @Test
    void rejectsValidProductPaddedPastLimit() throws Exception {
        postChunked(VALID + " ".repeat(LIMIT * 2)).andExpect(status().isPayloadTooLarge());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void rejectsTrailingGarbage() throws Exception {
        postChunked(VALID + " xyz").andExpect(status().isBadRequest());
        verify(productService, never()).createProduct(any());
    }

    private org.springframework.test.web.servlet.ResultActions postChunked(String json) throws Exception {
        return mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON)
                .with(chunked(json.getBytes(StandardCharsets.UTF_8))));
    }

    private static RequestPostProcessor chunked(byte[] body) {
        return request -> new ChunkedRequest(request, body);
    }

    private static final class ChunkedRequest extends MockHttpServletRequest {
        private final ServletInputStream body;

        ChunkedRequest(MockHttpServletRequest original, byte[] body) {
            super(original.getServletContext(), original.getMethod(), original.getRequestURI());
            setContentType(original.getContentType());
            this.body = new DelegatingServletInputStream(new ByteArrayInputStream(body));
        }

        @Override
        public ServletInputStream getInputStream() {
            return body;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }
    }
}
