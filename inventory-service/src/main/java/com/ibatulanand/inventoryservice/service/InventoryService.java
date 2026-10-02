package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCode) {
        return isInStock(skuCode, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCode, List<Integer> quantity) {
        if (quantity != null && quantity.size() != skuCode.size()) {
            throw new IllegalArgumentException("quantity must have one entry per skuCode");
        }

        List<Integer> requestedQuantities = IntStream.range(0, skuCode.size())
                .mapToObj(index -> quantity == null || quantity.get(index) == null ? 1 : quantity.get(index))
                .toList();
        if (requestedQuantities.stream().anyMatch(requestedQuantity -> requestedQuantity < 1)) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }

//        // Simulating Timeout
//        log.info("Wait Started");
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            log.info("Exception from Thread: ", e);
//        }
//        log.info("Wait Ended");

        Map<String, Integer> availableQuantities = inventoryRepository.findBySkuCodeIn(skuCode).stream()
                .collect(Collectors.toMap(
                        Inventory::getSkuCode,
                        inventory -> inventory.getQuantity() == null ? 0 : inventory.getQuantity(),
                        Integer::sum
                ));

        return IntStream.range(0, skuCode.size())
                .mapToObj(index ->
                        InventoryResponse.builder()
                                .skuCode(skuCode.get(index))
                                .isInStock(availableQuantities.getOrDefault(skuCode.get(index), 0)
                                        >= requestedQuantities.get(index))
                                .build()
                ).toList();
    }
}
