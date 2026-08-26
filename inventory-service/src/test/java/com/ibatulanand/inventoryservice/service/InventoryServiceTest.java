package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void should_report_in_stock_when_quantity_is_positive() {
        when(inventoryRepository.findBySkuCodeIn(List.of("iphone_15")))
                .thenReturn(List.of(TestFixtures.inventory("iphone_15", 3)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15"));

        assertThat(responses).containsExactly(TestFixtures.inventoryResponse("iphone_15", true));
    }

    @Test
    void should_report_out_of_stock_when_quantity_is_zero() {
        when(inventoryRepository.findBySkuCodeIn(List.of("iphone_15")))
                .thenReturn(List.of(TestFixtures.inventory("iphone_15", 0)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15"));

        assertThat(responses).containsExactly(TestFixtures.inventoryResponse("iphone_15", false));
    }

    @Test
    void should_omit_sku_from_response_when_no_inventory_row_exists() {
        when(inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "unknown_sku")))
                .thenReturn(List.of(TestFixtures.inventory("iphone_15", 1)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15", "unknown_sku"));

        assertThat(responses).containsExactly(TestFixtures.inventoryResponse("iphone_15", true));
        assertThat(responses).extracting(InventoryResponse::getSkuCode).doesNotContain("unknown_sku");
    }

    @Test
    void should_return_empty_list_when_sku_codes_are_empty() {
        when(inventoryRepository.findBySkuCodeIn(anyList())).thenReturn(List.of());

        assertThat(inventoryService.isInStock(List.of())).isEmpty();
    }
}
