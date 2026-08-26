package com.ibatulanand.orderservice;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TimeoutTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.application.name", "order-service-timeout-test");
    }
}
