package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("order-test-");
    private OrderController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, executor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void placeOrderReturns201WithServiceMessageAsynchronously() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn("Order Placed Successfully!");

        MvcResult pending = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderLineItemsDtoList\":[{\"skuCode\":\"iphone_15\",\"price\":999.99,\"quantity\":1}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isCreated())
                .andExpect(content().string("Order Placed Successfully!"));
    }

    @Test
    void placeOrderRunsOnTheSpringManagedExecutorNotTheCommonForkJoinPool() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class))).thenAnswer(invocation -> Thread.currentThread().getName());

        String threadName = controller.placeOrder(new OrderRequest(List.of())).get();

        assertThat(threadName).startsWith("order-test-");
    }

    @Test
    void fallbackReturnsFriendlyMessageWithoutCallingService() throws Exception {
        String message = controller.fallbackMethod(new OrderRequest(List.of()), new IllegalStateException("inventory down")).get();

        assertThat(message).isEqualTo("Oops! Something went wrong, please order after some time!");
        verifyNoInteractions(orderService);
    }
}
