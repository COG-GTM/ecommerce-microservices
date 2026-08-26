package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import com.ibatulanand.orderservice.support.TestFixtures;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.equalTo;

@WebMvcTest(OrderController.class)
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        TimeLimiterAutoConfiguration.class,
        RetryAutoConfiguration.class
})
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @Test
    void should_return_created_with_service_message_when_order_placed() {
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn("Order Placed Successfully!");

        RestAssuredMockMvc.given()
                .contentType(ContentType.JSON)
                .body(TestFixtures.orderRequest("iphone_13"))
                .when()
                .async()
                .post("/api/order")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body(equalTo("Order Placed Successfully!"));
    }

    @Test
    void should_return_fallback_message_when_service_throws() {
        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new RuntimeException("inventory-service is down"));

        RestAssuredMockMvc.given()
                .contentType(ContentType.JSON)
                .body(TestFixtures.orderRequest("iphone_13"))
                .when()
                .async()
                .post("/api/order")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body(equalTo("Oops! Something went wrong, please order after some time!"));
    }
}
