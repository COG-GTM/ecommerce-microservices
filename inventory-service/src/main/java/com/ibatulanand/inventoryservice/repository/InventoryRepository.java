package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findBySkuCodeIn(List<String> skuCode);

    List<Inventory> findBySkuCodeInAndStoreId(List<String> skuCode, String storeId);
}
