package com.ibatulanand.apigateway;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class OpenTelemetryTestExporter {

    private static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();

    @Produces
    @Singleton
    SpanExporter spanExporter() {
        return EXPORTER;
    }

    static InMemorySpanExporter exporter() {
        return EXPORTER;
    }

    static void reset() {
        EXPORTER.reset();
    }
}
