package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;

public class TestHelper {

    public static Inventory inventoryFixture(String skuCode, Integer quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }

    public static InventoryResponse inventoryResponseFixture(String skuCode, boolean inStock) {
        return InventoryResponse.builder().skuCode(skuCode).isInStock(inStock).build();
    }
}
