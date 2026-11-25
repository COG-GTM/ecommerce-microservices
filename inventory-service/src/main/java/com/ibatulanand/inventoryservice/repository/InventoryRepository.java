package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findBySkuCodeIn(List<String> skuCode);

    List<Inventory> findBySkuCodeOrderByIdAsc(String skuCode);

    boolean existsBySkuCode(String skuCode);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.quantity = i.quantity - :quantity where i.id = :id and i.quantity >= :quantity")
    int decrementIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.quantity = i.quantity + :quantity where i.id = :id")
    int increment(@Param("id") Long id, @Param("quantity") int quantity);
}
