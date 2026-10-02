package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.exception.OutOfStockException;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class,
        TimeLimiterAutoConfiguration.class
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private OrderService orderService;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("inventory").reset();
    }

    @Test
    void placeOrderReturnsCreatedWithOrderNumber() throws Exception {
        when(orderService.placeOrder(any())).thenReturn("ord-123");

        MvcResult result = postOrder();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ord-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.skuCodes").doesNotExist());
    }

    @Test
    void placeOrderReturnsConflictWithOutOfStockSkusWithoutRetrying() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new OutOfStockException(List.of("sku_a", "sku_b")));

        MvcResult result = postOrder();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.skuCodes").isArray())
                .andExpect(jsonPath("$.skuCodes[0]").value("sku_a"))
                .andExpect(jsonPath("$.skuCodes[1]").value("sku_b"));
        verify(orderService, times(1)).placeOrder(any());
    }

    @Test
    void placeOrderReturnsServiceUnavailableWhenInventoryIsDown() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new RuntimeException("inventory unavailable"));

        MvcResult result = postOrder();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void placeOrderReturnsServiceUnavailableWhenCircuitIsOpen() throws Exception {
        circuitBreakerRegistry.circuitBreaker("inventory").transitionToOpenState();

        MvcResult result = postOrder();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("SERVICE_UNAVAILABLE"));
        verify(orderService, never()).placeOrder(any());
    }

    private MvcResult postOrder() throws Exception {
        return mockMvc.perform(post("/api/order")
                        .contentType(APPLICATION_JSON)
                        .content("{\"orderLineItemsDtoList\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }
}
