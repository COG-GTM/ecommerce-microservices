package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySkuCodeAndStoreId(String skuCode, String storeId);

    List<Inventory> findBySkuCodeInAndStoreId(List<String> skuCodes, String storeId);

    List<Inventory> findBySkuCodeAndStoreIdIn(String skuCode, Collection<String> storeIds);
}
