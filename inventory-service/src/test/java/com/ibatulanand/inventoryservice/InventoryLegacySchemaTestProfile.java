package com.ibatulanand.inventoryservice;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class InventoryLegacySchemaTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.hibernate-orm.database.generation", "validate");
    }
}
