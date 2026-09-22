package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryRepository inventoryRepository;

    @Test
    void isInStock_returnsJsonArrayForRequestedSkus() throws Exception {
        when(inventoryService.isInStock(List.of("a", "b"))).thenReturn(List.of(
                new InventoryResponse("a", true),
                new InventoryResponse("b", false)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "a", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].skuCode").value("a"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].skuCode").value("b"))
                .andExpect(jsonPath("$[1].inStock").value(false));
    }

    @Test
    void isInStock_returnsEmptyArrayWhenNothingMatches() throws Exception {
        when(inventoryService.isInStock(List.of("unknown"))).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory").param("skuCode", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void isInStock_missingSkuCodeParamIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isBadRequest());
    }
}
