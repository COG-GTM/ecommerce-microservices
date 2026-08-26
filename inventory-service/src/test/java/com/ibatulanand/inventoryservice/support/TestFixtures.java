package com.ibatulanand.inventoryservice.support;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;

/** Central place to build inventory test data, so a model change touches one file. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Inventory inventory(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }

    public static InventoryResponse inventoryResponse(String skuCode, boolean inStock) {
        return InventoryResponse.builder().skuCode(skuCode).isInStock(inStock).build();
    }
}
