package com.ibatulanand.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.TestHelper;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void should_place_order_success() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn("Order Placed Successfully!");

        MvcResult result = mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestHelper.orderRequestFixture("iphone_15"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(content().string("Order Placed Successfully!"));
    }

    @Test
    void should_return_fallback_message_when_inventory_call_fails() throws Exception {
        String fallback = new OrderController(orderService)
                .fallbackMethod(TestHelper.orderRequestFixture("iphone_15"), new RuntimeException("boom"))
                .get();

        assertEquals("Oops! Something went wrong, please order after some time!", fallback);
    }
}
