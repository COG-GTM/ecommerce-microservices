package com.ibatulanand.productservice.controller;

import com.ibatulanand.productservice.service.ProductService;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerValidationTest {

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService)).build();
    }

    @Test
    void acceptsValidProduct() throws Exception {
        postJson(body("Iphone 15", "Apple Iphone 15", "1500.00")).andExpect(status().isCreated());
        verify(productService).createProduct(any());
    }

    @Test
    void rejectsOneMegabyteName() throws Exception {
        postJson(body("a".repeat(1024 * 1024), "desc", "10")).andExpect(status().isBadRequest());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void rejectsNameOverLimit() throws Exception {
        postJson(body("a".repeat(201), "desc", "10")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDescriptionOverLimit() throws Exception {
        postJson(body("name", "d".repeat(2001), "10")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankName() throws Exception {
        postJson(body(" ", "desc", "10")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativePrice() throws Exception {
        postJson(body("name", "desc", "-1")).andExpect(status().isBadRequest());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void rejectsMissingPrice() throws Exception {
        postJson("{\"name\":\"name\",\"description\":\"desc\"}").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPriceWithTooManyDigits() throws Exception {
        postJson(body("name", "desc", "1e30")).andExpect(status().isBadRequest());
        postJson(body("name", "desc", "1.001")).andExpect(status().isBadRequest());
        postJson(body("name", "desc", "1e2147483647")).andExpect(status().isBadRequest());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void acceptsJsonSuffixMediaTypes() throws Exception {
        mockMvc.perform(post("/api/product").contentType("application/vnd.product+json")
                        .content(body("Iphone 15", "Apple Iphone 15", "1500.00")))
                .andExpect(status().isCreated());
        verify(productService).createProduct(any());
    }

    @Test
    void rejectsFormPostWithoutReadingIt() throws Exception {
        mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("name=n&description=d&price=1")
                        .with(request -> new UnreadableRequest(request)))
                .andExpect(status().isUnsupportedMediaType());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void passesPagingParametersToService() throws Exception {
        mockMvc.perform(get("/api/product").param("page", "2").param("size", "1000"))
                .andExpect(status().isOk());
        verify(productService).getAllProducts(2, 1000);
    }

    @Test
    void usesDefaultPaging() throws Exception {
        mockMvc.perform(get("/api/product")).andExpect(status().isOk());
        verify(productService).getAllProducts(0, ProductService.DEFAULT_PAGE_SIZE);
    }

    private static final class UnreadableRequest extends MockHttpServletRequest {
        UnreadableRequest(MockHttpServletRequest original) {
            super(original.getServletContext(), original.getMethod(), original.getRequestURI());
            setContentType(original.getContentType());
        }

        @Override
        public ServletInputStream getInputStream() {
            throw new AssertionError("body was read");
        }

        @Override
        public BufferedReader getReader() {
            throw new AssertionError("body was read");
        }

        @Override
        public String getParameter(String name) {
            throw new AssertionError("parameters were parsed");
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            throw new AssertionError("parameters were parsed");
        }

        @Override
        public Enumeration<String> getParameterNames() {
            throw new AssertionError("parameters were parsed");
        }

        @Override
        public String[] getParameterValues(String name) {
            throw new AssertionError("parameters were parsed");
        }
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/product").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, String description, String price) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\",\"price\":" + price + "}";
    }
}
