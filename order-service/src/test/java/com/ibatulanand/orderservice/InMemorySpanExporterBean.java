package com.ibatulanand.orderservice;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class InMemorySpanExporterBean implements SpanExporter {

    private final InMemorySpanExporter delegate = InMemorySpanExporter.create();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    public List<SpanData> finishedSpanItems() {
        return delegate.getFinishedSpanItems();
    }

    public void reset() {
        delegate.reset();
    }
}
