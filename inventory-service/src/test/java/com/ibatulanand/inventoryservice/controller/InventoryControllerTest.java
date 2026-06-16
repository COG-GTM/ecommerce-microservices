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

import static org.mockito.ArgumentMatchers.anyList;
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
    void isInStock_returnsOk() throws Exception {
        when(inventoryService.isInStock(List.of("sku1")))
                .thenReturn(List.of(new InventoryResponse("sku1", true)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "sku1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("sku1"))
                .andExpect(jsonPath("$[0].inStock").value(true));
    }

    @Test
    void isInStock_multipleSkuCodes() throws Exception {
        when(inventoryService.isInStock(anyList()))
                .thenReturn(List.of(
                        new InventoryResponse("sku1", true),
                        new InventoryResponse("sku2", false)));

        mockMvc.perform(get("/api/inventory").param("skuCode", "sku1", "sku2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("sku1"))
                .andExpect(jsonPath("$[1].skuCode").value("sku2"));
    }
}
