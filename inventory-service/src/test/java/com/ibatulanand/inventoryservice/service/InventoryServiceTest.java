package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.TestHelper;
import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void should_report_product_in_stock_when_quantity_is_positive() {
        when(inventoryRepository.findBySkuCodeIn(anyList()))
                .thenReturn(List.of(TestHelper.inventoryFixture("iphone_15", 10)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15"));

        assertEquals(1, responses.size());
        assertEquals("iphone_15", responses.get(0).getSkuCode());
        assertTrue(responses.get(0).isInStock());
    }

    @Test
    void should_report_product_out_of_stock_when_quantity_is_zero() {
        when(inventoryRepository.findBySkuCodeIn(anyList()))
                .thenReturn(List.of(TestHelper.inventoryFixture("iphone_15", 0)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15"));

        assertFalse(responses.get(0).isInStock());
    }

    @Test
    void should_return_empty_list_when_no_inventory_found() {
        when(inventoryRepository.findBySkuCodeIn(anyList())).thenReturn(List.of());

        assertTrue(inventoryService.isInStock(List.of("unknown_sku")).isEmpty());
    }
}
