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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void bindsAndPassesRepeatedQuantityParametersAndUsesInStockWireField() throws Exception {
        when(inventoryService.isInStock(List.of("one", "two"), List.of(5, 1)))
                .thenReturn(List.of(
                        InventoryResponse.builder().skuCode("one").isInStock(true).build(),
                        InventoryResponse.builder().skuCode("two").isInStock(false).build()
                ));

        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "one", "two")
                        .param("quantity", "5", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].inStock").value(false));

        verify(inventoryService).isInStock(List.of("one", "two"), List.of(5, 1));
    }

    @Test
    void passesNullQuantityWhenQuantityParameterIsAbsent() throws Exception {
        when(inventoryService.isInStock(List.of("sku"), null)).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory").param("skuCode", "sku"))
                .andExpect(status().isOk());

        verify(inventoryService).isInStock(List.of("sku"), null);
    }

    @Test
    void returnsBadRequestWhenServiceRejectsRequest() throws Exception {
        doThrow(new IllegalArgumentException("quantity must have one entry per skuCode"))
                .when(inventoryService).isInStock(List.of("sku"), List.of(1, 2));

        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "sku")
                        .param("quantity", "1", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("quantity must have one entry per skuCode"));
    }
}
