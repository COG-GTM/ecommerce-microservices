package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class InventoryTestData {

    @Inject
    InventoryRepository inventoryRepository;

    @Transactional
    public void clean() {
        inventoryRepository.deleteAll();
    }

    @Transactional
    public void seed(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        inventoryRepository.persist(inventory);
    }
}
