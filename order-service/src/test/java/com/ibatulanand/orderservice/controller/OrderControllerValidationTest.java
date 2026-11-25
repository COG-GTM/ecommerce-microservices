package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerValidationTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void rejectsMoreThanMaxLineItems() throws Exception {
        postOrder(itemsBody(OrderRequest.MAX_LINE_ITEMS + 1)).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void acceptsMaxLineItems() throws Exception {
        when(orderService.placeOrder(any())).thenReturn("Order Placed Successfully!");

        MvcResult result = postOrder(itemsBody(OrderRequest.MAX_LINE_ITEMS))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isCreated());
        verify(orderService).placeOrder(any());
    }

    @Test
    void rejectsEmptyOrMissingLineItems() throws Exception {
        postOrder("{\"orderLineItemsDtoList\":[]}").andExpect(status().isBadRequest());
        postOrder("{}").andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsNullOrEmptyItems() throws Exception {
        postOrder("{\"orderLineItemsDtoList\":[null]}").andExpect(status().isBadRequest());
        postOrder("{\"orderLineItemsDtoList\":[{}]}").andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsOverlongSkuCode() throws Exception {
        String sku = "x".repeat(51);
        postOrder("{\"orderLineItemsDtoList\":[" + item(sku) + "]}").andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    private org.springframework.test.web.servlet.ResultActions postOrder(String body) throws Exception {
        return mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String itemsBody(int count) {
        return "{\"orderLineItemsDtoList\":[" + String.join(",", Collections.nCopies(count, item("iphone_15"))) + "]}";
    }

    private static String item(String sku) {
        return "{\"skuCode\":\"" + sku + "\",\"price\":1200,\"quantity\":1}";
    }
}
