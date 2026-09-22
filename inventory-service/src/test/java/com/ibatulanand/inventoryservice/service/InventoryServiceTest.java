package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void isInStock_positiveQuantityIsInStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("iphone_15")))
                .thenReturn(List.of(new Inventory(1L, "iphone_15", 100)));

        List<InventoryResponse> result = inventoryService.isInStock(List.of("iphone_15"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSkuCode()).isEqualTo("iphone_15");
        assertThat(result.get(0).isInStock()).isTrue();
    }

    @Test
    void isInStock_zeroQuantityIsNotInStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("iphone_15_pro")))
                .thenReturn(List.of(new Inventory(2L, "iphone_15_pro", 0)));

        List<InventoryResponse> result = inventoryService.isInStock(List.of("iphone_15_pro"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSkuCode()).isEqualTo("iphone_15_pro");
        assertThat(result.get(0).isInStock()).isFalse();
    }

    @Test
    void isInStock_mixedSkusMapEachRow() {
        List<String> skus = List.of("iphone_15", "iphone_15_pro");
        when(inventoryRepository.findBySkuCodeIn(skus)).thenReturn(List.of(
                new Inventory(1L, "iphone_15", 100),
                new Inventory(2L, "iphone_15_pro", 0)));

        List<InventoryResponse> result = inventoryService.isInStock(skus);

        assertThat(result)
                .extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", true),
                        org.assertj.core.groups.Tuple.tuple("iphone_15_pro", false));
        verify(inventoryRepository).findBySkuCodeIn(skus);
    }

    @Test
    void isInStock_unknownSkuReturnsEmptyList() {
        when(inventoryRepository.findBySkuCodeIn(List.of("unknown"))).thenReturn(List.of());

        assertThat(inventoryService.isInStock(List.of("unknown"))).isEmpty();
    }
}
