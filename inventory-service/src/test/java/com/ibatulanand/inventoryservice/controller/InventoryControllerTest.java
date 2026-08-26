package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = InventoryController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        }
)
@ContextConfiguration(classes = InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void returnsSingleInventoryStatus() throws Exception {
        when(inventoryService.isInStock(List.of("a")))
                .thenReturn(List.of(InventoryResponse.builder().skuCode("a").isInStock(true).build()));

        mockMvc.perform(get("/api/inventory").queryParam("skuCode", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("a"))
                .andExpect(jsonPath("$[0].inStock").value(true));

        verify(inventoryService).isInStock(eq(List.of("a")));
    }

    @Test
    void returnsStatusesForMultipleSkuCodes() throws Exception {
        when(inventoryService.isInStock(List.of("a", "b")))
                .thenReturn(List.of(
                        InventoryResponse.builder().skuCode("a").isInStock(true).build(),
                        InventoryResponse.builder().skuCode("b").isInStock(false).build()));

        mockMvc.perform(get("/api/inventory").queryParam("skuCode", "a", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("a"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].skuCode").value("b"))
                .andExpect(jsonPath("$[1].inStock").value(false));

        verify(inventoryService).isInStock(eq(List.of("a", "b")));
    }
}
