package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCode, String storeId) {
        List<Inventory> inventories = storeId == null
                ? inventoryRepository.findBySkuCodeIn(skuCode)
                : inventoryRepository.findBySkuCodeInAndStoreId(skuCode, storeId);

        return inventories.stream()
                .map(inventory -> {
                    int onHand = inventory.getOnHand() == null ? 0 : inventory.getOnHand();
                    return InventoryResponse.builder()
                            .skuCode(inventory.getSkuCode())
                            .storeId(inventory.getStoreId())
                            .onHand(onHand)
                            .isInStock(onHand > 0)
                            .shipFromStoreEligible(inventory.isShipFromStoreEligible())
                            .build();
                }).toList();
    }
}
