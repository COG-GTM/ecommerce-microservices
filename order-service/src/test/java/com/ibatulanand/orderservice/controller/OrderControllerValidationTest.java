package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void errorBodyListsFieldsWithoutEchoingRejectedValues() throws Exception {
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderLineItemsDtoList\":[{\"skuCode\":\"secret_sku_value\",\"price\":1e2147483647,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid order request"))
                .andExpect(jsonPath("$.fields[0]").value(containsString("orderLineItemsDtoList[0].price")))
                .andExpect(content().string(not(containsString("secret_sku_value"))));
        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void oversizedListOfInvalidItemsReportsOnlyTheListSize() throws Exception {
        String item = "{\"skuCode\":\"\",\"price\":-1.001,\"quantity\":0}";
        String body = "{\"orderLineItemsDtoList\":[" + String.join(",", Collections.nCopies(101, item)) + "]}";
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.fields[0]").value(startsWith("orderLineItemsDtoList: ")));
        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void malformedOrMissingBodyUsesTheSameErrorEnvelope() throws Exception {
        for (String body : new String[] {"{\"orderLineItemsDtoList\":[", "", "{\"orderLineItemsDtoList\":[{\"price\":\"secret_price\"}]}"}) {
            mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid order request"))
                    .andExpect(jsonPath("$.fields").isArray())
                    .andExpect(content().string(not(containsString("secret_price"))));
        }
        verify(orderService, never()).placeOrder(any());
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
