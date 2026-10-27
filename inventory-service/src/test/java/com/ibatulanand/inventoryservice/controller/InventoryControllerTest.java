package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService)).build();
    }

    @Test
    void bindsRepeatedSkuCodeParamsAndReturnsStockFlags() throws Exception {
        when(inventoryService.isInStock(List.of("iphone_15", "iphone_15_pro"))).thenReturn(List.of(
                new InventoryResponse("iphone_15", true),
                new InventoryResponse("iphone_15_pro", false)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15", "iphone_15_pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("iphone_15"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].inStock").value(false));
    }

    @Test
    void missingSkuCodeParamIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/inventory")).andExpect(status().isBadRequest());
    }
}
