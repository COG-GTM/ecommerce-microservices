package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private void assertRejected(String body) throws Exception {
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(orderService, never()).placeOrder(any());
    }

    @Test
    @Timeout(10)
    void rejectsPriceWithHugeExponent() throws Exception {
        assertRejected("{\"orderLineItemsDtoList\":[{\"skuCode\":\"unknown_sku\",\"price\":1e1000000000,\"quantity\":1}]}");
    }

    @Test
    void rejectsNegativePrice() throws Exception {
        assertRejected("{\"orderLineItemsDtoList\":[{\"skuCode\":\"iphone_13\",\"price\":-5,\"quantity\":1}]}");
    }

    @Test
    void rejectsMissingItems() throws Exception {
        assertRejected("{}");
    }
}
