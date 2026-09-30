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
import java.util.Map;
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

        return inventoryRepository.findBySkuCodeIn(skuCode).stream()
                .map(inventory ->
                        InventoryResponse.builder()
                                .skuCode(inventory.getSkuCode())
                                .isInStock(inventory.getQuantity() > 0)
                                .build()
                ).toList();
    }

    /**
     * Atomically decrements stock for every requested SKU, or for none of them.
     * Rows are locked for the duration of the transaction so concurrent
     * reservations cannot oversell.
     */
    @Transactional
    public void reserve(List<StockReservationItem> items) {
        Map<String, Integer> requested = aggregate(items);
        Map<String, List<Inventory>> rowsBySku = lockRows(requested);

        List<String> unavailable = requested.entrySet().stream()
                .filter(entry -> available(rowsBySku.get(entry.getKey())) < entry.getValue())
                .map(Map.Entry::getKey)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new InsufficientStockException(unavailable);
        }

        requested.forEach((skuCode, quantity) -> {
            int remaining = quantity;
            for (Inventory row : rowsBySku.get(skuCode)) {
                int taken = Math.min(remaining, quantityOf(row));
                row.setQuantity(quantityOf(row) - taken);
                remaining -= taken;
                if (remaining == 0) {
                    break;
                }
            }
        });
        inventoryRepository.saveAll(rowsBySku.values().stream().flatMap(List::stream).toList());
    }

    /**
     * Returns previously reserved stock, e.g. when the order could not be persisted.
     */
    @Transactional
    public void release(List<StockReservationItem> items) {
        Map<String, Integer> requested = aggregate(items);
        Map<String, List<Inventory>> rowsBySku = lockRows(requested);

        List<String> unknown = requested.keySet().stream()
                .filter(skuCode -> rowsBySku.get(skuCode).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown skuCode(s): " + String.join(", ", unknown));
        }

        requested.forEach((skuCode, quantity) -> {
            Inventory row = rowsBySku.get(skuCode).get(0);
            row.setQuantity(quantityOf(row) + quantity);
        });
        inventoryRepository.saveAll(rowsBySku.values().stream().flatMap(List::stream).toList());
    }

    private Map<String, Integer> aggregate(List<StockReservationItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (items.size() > MAX_ITEMS_PER_RESERVATION) {
            throw new IllegalArgumentException("Too many items");
        }
        Map<String, Integer> requested = new LinkedHashMap<>();
        for (StockReservationItem item : items) {
            if (item == null || item.getSkuCode() == null || item.getSkuCode().isBlank()) {
                throw new IllegalArgumentException("skuCode is required");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            int total = requested.merge(item.getSkuCode(), item.getQuantity(), Math::addExact);
            if (total > MAX_QUANTITY_PER_SKU) {
                throw new IllegalArgumentException("quantity exceeds the per-SKU limit");
            }
        }
        return requested;
    }

    private Map<String, List<Inventory>> lockRows(Map<String, Integer> requested) {
        Map<String, List<Inventory>> rowsBySku = inventoryRepository.lockBySkuCodeIn(requested.keySet()).stream()
                .collect(Collectors.groupingBy(Inventory::getSkuCode, LinkedHashMap::new, Collectors.toList()));
        requested.keySet().forEach(skuCode -> rowsBySku.putIfAbsent(skuCode, new ArrayList<>()));
        return rowsBySku;
    }

    private static long available(List<Inventory> rows) {
        return rows.stream().mapToLong(InventoryService::quantityOf).sum();
    }

    private static int quantityOf(Inventory row) {
        return row.getQuantity() == null ? 0 : Math.max(row.getQuantity(), 0);
    }
}
