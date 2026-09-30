package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findBySkuCodeIn(List<String> skuCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.skuCode in :skuCodes order by i.id")
    List<Inventory> lockBySkuCodeIn(@Param("skuCodes") Collection<String> skuCodes);
}
