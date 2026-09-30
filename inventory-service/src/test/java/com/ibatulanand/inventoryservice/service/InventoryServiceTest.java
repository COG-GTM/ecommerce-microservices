package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.StockReservationItem;
import com.ibatulanand.inventoryservice.exception.InsufficientStockException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final List<Inventory> rows = new ArrayList<>();
    private InventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        when(repository.lockBySkuCodeIn(any())).thenAnswer(invocation -> {
            Collection<String> skuCodes = invocation.getArgument(0);
            return rows.stream().filter(row -> skuCodes.contains(row.getSkuCode())).toList();
        });
        service = new InventoryService(repository);
        rows.add(new Inventory(1L, "iphone_15", 100));
        rows.add(new Inventory(2L, "iphone_15_pro", 0));
    }

    @Test
    void decrementsStockForReservedItems() {
        service.reserve(List.of(new StockReservationItem("iphone_15", 3)));

        assertEquals(97, rows.get(0).getQuantity());
        verify(repository).saveAll(anyIterable());
    }

    @Test
    void rejectsUnknownSkuWithoutTouchingStock() {
        InsufficientStockException e = assertThrows(InsufficientStockException.class, () -> service.reserve(List.of(
                new StockReservationItem("iphone_15", 1),
                new StockReservationItem("nonexistent", 1))));

        assertEquals(List.of("nonexistent"), e.getUnavailableSkuCodes());
        assertEquals(100, rows.get(0).getQuantity());
        verify(repository, never()).saveAll(anyIterable());
    }

    @Test
    void rejectsQuantityAboveAvailableStock() {
        assertThrows(InsufficientStockException.class,
                () -> service.reserve(List.of(new StockReservationItem("iphone_15_pro", 1))));
        assertThrows(InsufficientStockException.class,
                () -> service.reserve(List.of(new StockReservationItem("iphone_15", 101))));
        assertEquals(100, rows.get(0).getQuantity());
    }

    @Test
    void aggregatesRepeatedSkusBeforeCheckingStock() {
        assertThrows(InsufficientStockException.class, () -> service.reserve(List.of(
                new StockReservationItem("iphone_15", 60),
                new StockReservationItem("iphone_15", 60))));
        assertEquals(100, rows.get(0).getQuantity());
    }

    @Test
    void drawsFromDuplicateRowsOfTheSameSku() {
        rows.add(new Inventory(3L, "iphone_15", 5));

        service.reserve(List.of(new StockReservationItem("iphone_15", 103)));

        assertEquals(0, rows.get(0).getQuantity());
        assertEquals(2, rows.get(2).getQuantity());
    }

    @Test
    void rejectsNonPositiveOrMissingInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(List.of(new StockReservationItem("iphone_15", 0))));
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(List.of(new StockReservationItem("iphone_15", -5))));
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(List.of(new StockReservationItem(" ", 1))));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(null));
        assertEquals(100, rows.get(0).getQuantity());
    }

    @Test
    void releaseRestoresStock() {
        service.reserve(List.of(new StockReservationItem("iphone_15", 10)));
        service.release(List.of(new StockReservationItem("iphone_15", 10)));

        assertEquals(100, rows.get(0).getQuantity());
    }
}
