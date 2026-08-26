package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.AbstractIntegrationTest;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Resilience4j circuit breaker / retry / time limiter wiring on the controller: a
 * failing inventory call must resolve to the fallback response instead of an error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerResilienceTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private OrderService orderService;

    @Test
    void returnsFallbackResponseWhenInventoryCallFails() {
        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new RuntimeException("inventory-service unavailable"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/order", orderRequest(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("Oops! Something went wrong, please order after some time!");
    }

    @Test
    void returnsFallbackResponseWhenTheDomainRejectsTheOrder() {
        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new IllegalArgumentException("Product is not in stock, please try again later"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/order", orderRequest(), String.class);

        assertThat(response.getBody()).isEqualTo("Oops! Something went wrong, please order after some time!");
        verify(orderService, atLeastOnce()).placeOrder(any(OrderRequest.class));
    }

    @Test
    void abortsCallsExceedingTheTimeLimiterDuration() {
        when(orderService.placeOrder(any(OrderRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return "Order Placed Successfully!";
        });

        ResponseEntity<String> response = restTemplate.postForEntity("/api/order", orderRequest(), String.class);

        // The time limiter fails with a TimeoutException, which the RuntimeException fallback does
        // not handle, so the request errors out instead of returning a successful order.
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(response.getBody()).doesNotContain("Order Placed Successfully!");
    }

    private static OrderRequest orderRequest() {
        return new OrderRequest(List.of(
                new OrderLineItemsDto(null, "iphone_13", new BigDecimal("1200"), 1)));
    }
}
