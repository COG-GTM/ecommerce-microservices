package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class InventoryRepository implements PanacheRepository<Inventory> {

    public List<Inventory> findBySkuCodeIn(List<String> skuCodes) {
        if (skuCodes.isEmpty()) {
            return List.of();
        }
        return find("skuCode in :skuCodes order by id", Parameters.with("skuCodes", skuCodes)).list();
    }
}
