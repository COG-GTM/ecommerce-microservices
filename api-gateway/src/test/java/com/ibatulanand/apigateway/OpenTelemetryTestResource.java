package com.ibatulanand.apigateway;

import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class OpenTelemetryTestResource implements QuarkusTestResourceLifecycleManager {

    private static InMemorySpanExporter exporter;

    @Override
    public Map<String, String> start() {
        install();
        return Map.of("quarkus.otel.exporter.otlp.enabled", "false");
    }

    static void install() {
        GlobalOpenTelemetry.resetForTest();
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @Override
    public void stop() {
        if (exporter != null) {
            exporter.reset();
            exporter = null;
        }
        GlobalOpenTelemetry.resetForTest();
    }

    public static InMemorySpanExporter exporter() {
        return exporter;
    }
}
