package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.StockReservationItem;
import com.ibatulanand.inventoryservice.exception.InsufficientStockException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    static final int MAX_ITEMS_PER_RESERVATION = 100;
    static final int MAX_QUANTITY_PER_SKU = 1000;

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCode) {
//        // Simulating Timeout
//        log.info("Wait Started");
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            log.info("Exception from Thread: ", e);
//        }
//        log.info("Wait Ended");

        Set<String> inStock = inventoryRepository.findBySkuCodeIn(skuCode).stream()
                .filter(inventory -> inventory.getQuantity() != null && inventory.getQuantity() > 0)
                .map(inventory -> inventory.getSkuCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return skuCode.stream()
                .distinct()
                .map(code -> InventoryResponse.builder()
                        .skuCode(code)
                        .isInStock(inStock.contains(code.toLowerCase(Locale.ROOT)))
                        .build()
                ).toList();
    }

    /**
     * Reserves stock for every requested SKU or for none of them. Each decrement is a
     * conditional UPDATE ({@code quantity >= requested}), so concurrent reservations
     * cannot take the same units twice.
     */
    @Transactional
    public void reserve(List<StockReservationItem> items) {
        Map<String, Integer> requested = aggregate(items);
        List<String> unavailable = new ArrayList<>();
        requested.forEach((skuCode, quantity) -> {
            boolean reserved = inventoryRepository.findBySkuCodeOrderByIdAsc(skuCode).stream()
                    .anyMatch(row -> inventoryRepository.decrementIfAvailable(row.getId(), quantity) == 1);
            if (!reserved) {
                unavailable.add(skuCode);
            }
        });
        if (!unavailable.isEmpty()) {
            throw new InsufficientStockException(unavailable);
        }
    }

    /**
     * Returns previously reserved stock, e.g. when the order could not be persisted.
     */
    @Transactional
    public void release(List<StockReservationItem> items) {
        Map<String, Integer> requested = aggregate(items);
        requested.forEach((skuCode, quantity) -> {
            List<Inventory> rows = inventoryRepository.findBySkuCodeOrderByIdAsc(skuCode);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Unknown skuCode: " + skuCode);
            }
            inventoryRepository.increment(rows.get(0).getId(), quantity);
        });
    }

    private static Map<String, Integer> aggregate(List<StockReservationItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (items.size() > MAX_ITEMS_PER_RESERVATION) {
            throw new IllegalArgumentException("At most " + MAX_ITEMS_PER_RESERVATION + " items are allowed");
        }
        Map<String, Integer> requested = new LinkedHashMap<>();
        Map<String, Integer> requestedByFoldedSku = new LinkedHashMap<>();
        for (StockReservationItem item : items) {
            if (item == null || item.getSkuCode() == null || item.getSkuCode().isBlank()) {
                throw new IllegalArgumentException("skuCode is required");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("quantity must be at least 1");
            }
            if (item.getQuantity() > MAX_QUANTITY_PER_SKU) {
                throw new IllegalArgumentException("quantity must be at most " + MAX_QUANTITY_PER_SKU);
            }
            requested.merge(item.getSkuCode(), item.getQuantity(), Integer::sum);
            int total = requestedByFoldedSku.merge(item.getSkuCode().toLowerCase(Locale.ROOT), item.getQuantity(), Integer::sum);
            if (total > MAX_QUANTITY_PER_SKU) {
                throw new IllegalArgumentException("quantity must be at most " + MAX_QUANTITY_PER_SKU);
            }
        }
        return requested;
    }
}
