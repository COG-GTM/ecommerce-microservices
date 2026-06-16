package com.ibatulanand.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private OrderService orderService;

    @Test
    void placeOrder_returnsCreated() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn("Order Placed Successfully!");

        OrderLineItemsDto dto = new OrderLineItemsDto(1L, "sku1", BigDecimal.valueOf(120), 2);
        OrderRequest request = new OrderRequest();
        request.setOrderLineItemsDtoList(List.of(dto));
        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult mvcResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated());
    }

    @Test
    void fallbackMethod_returnsErrorMessage() throws Exception {
        OrderController orderController = new OrderController(orderService);
        CompletableFuture<String> result = orderController.fallbackMethod(new OrderRequest(), new RuntimeException("boom"));
        assertEquals("Oops! Something went wrong, please order after some time!", result.get());
    }
}
