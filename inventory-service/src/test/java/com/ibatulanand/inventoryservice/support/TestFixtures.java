package com.ibatulanand.inventoryservice.support;

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
}
