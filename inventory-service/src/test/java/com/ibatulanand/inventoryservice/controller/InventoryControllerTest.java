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
import java.util.Optional;

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
    void shouldReturnStockForSkuCodes() throws Exception {
        when(inventoryService.isInStock(List.of("iphone_15")))
                .thenReturn(List.of(inventoryResponse("iphone_15", true)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("iphone_15"))
                .andExpect(jsonPath("$[0].inStock").value(true));
    }

    @Test
    void shouldReturnStockForSingleSkuCode() throws Exception {
        when(inventoryService.isInStock("iphone_15_pro"))
                .thenReturn(Optional.of(inventoryResponse("iphone_15_pro", false)));

        mockMvc.perform(get("/api/inventory/iphone_15_pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("iphone_15_pro"))
                .andExpect(jsonPath("$.inStock").value(false));
    }

    @Test
    void shouldReturnNotFoundForUnknownSkuCode() throws Exception {
        when(inventoryService.isInStock("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory/unknown"))
                .andExpect(status().isNotFound());
    }

    private InventoryResponse inventoryResponse(String skuCode, boolean inStock) {
        return InventoryResponse.builder().skuCode(skuCode).isInStock(inStock).build();
    }
}
