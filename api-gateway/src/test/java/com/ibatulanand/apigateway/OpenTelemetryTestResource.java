package com.ibatulanand.apigateway;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class OpenTelemetryTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        reset();
        return Map.of();
    }

    @Override
    public void stop() {
        reset();
    }

    public static io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter() {
        return OpenTelemetryTestExporter.exporter();
    }

    public static void reset() {
        OpenTelemetryTestExporter.reset();
    }
}
