package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.NearbyStoreAvailability;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
import com.ibatulanand.inventoryservice.exception.InventoryNotFoundException;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
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

    @Test
    void storeQueryReturnsFullInventoryObject() throws Exception {
        when(inventoryService.getStoreInventory("sku", "1969")).thenReturn(StoreInventoryResponse.builder()
                .skuCode("sku")
                .storeId("1969")
                .onHand(23)
                .nearbyStores(List.of(NearbyStoreAvailability.builder()
                        .storeId("1042")
                        .storeName("Union Square")
                        .distanceMiles(2.4)
                        .onHand(11)
                        .build()))
                .shipFromStoreEligible(true)
                .floor("Floor 1")
                .fixture("Fixture T-12")
                .build());

        mockMvc.perform(get("/api/inventory").param("skuCode", "sku").param("storeId", "1969"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("sku"))
                .andExpect(jsonPath("$.storeId").value("1969"))
                .andExpect(jsonPath("$.onHand").value(23))
                .andExpect(jsonPath("$.nearbyStores[0].storeId").value("1042"))
                .andExpect(jsonPath("$.nearbyStores[0].storeName").value("Union Square"))
                .andExpect(jsonPath("$.nearbyStores[0].distanceMiles").value(2.4))
                .andExpect(jsonPath("$.nearbyStores[0].onHand").value(11))
                .andExpect(jsonPath("$.shipFromStoreEligible").value(true))
                .andExpect(jsonPath("$.floor").value("Floor 1"))
                .andExpect(jsonPath("$.fixture").value("Fixture T-12"));
    }

    @Test
    void queryWithoutStoreIdReturnsLegacyArray() throws Exception {
        when(inventoryService.isInStock(List.of("a", "b"))).thenReturn(List.of(
                InventoryResponse.builder().skuCode("a").isInStock(true).build(),
                InventoryResponse.builder().skuCode("b").isInStock(false).build()));

        mockMvc.perform(get("/api/inventory").param("skuCode", "a", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("a"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].skuCode").value("b"))
                .andExpect(jsonPath("$[1].inStock").value(false));

        verify(inventoryService).isInStock(List.of("a", "b"));
    }

    @Test
    void missingInventoryReturnsNotFound() throws Exception {
        when(inventoryService.getStoreInventory("missing", "1969"))
                .thenThrow(new InventoryNotFoundException());

        mockMvc.perform(get("/api/inventory").param("skuCode", "missing").param("storeId", "1969"))
                .andExpect(status().isNotFound());
    }
}
