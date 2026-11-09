package com.ibatulanand.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class MdcPropagationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void asyncTaskSeesCallerCorrelationIdAndWorkerMdcIsRestored() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put("worker", "stale")).get();
            MDC.put(CorrelationIds.MDC_KEY, "req-1");

            String seen = CompletableFuture.supplyAsync(
                    MdcPropagation.wrap(() -> MDC.get(CorrelationIds.MDC_KEY) + "|" + MDC.get("worker")),
                    executor).get();

            assertThat(seen).isEqualTo("req-1|null");
            assertThat(executor.submit(() -> MDC.get("worker")).get()).isEqualTo("stale");
            assertThat(executor.submit(() -> MDC.get(CorrelationIds.MDC_KEY)).get()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void emptyCallerContextClearsMdcDuringTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put(CorrelationIds.MDC_KEY, "leftover")).get();

            String seen = CompletableFuture.supplyAsync(
                    MdcPropagation.wrap(() -> MDC.get(CorrelationIds.MDC_KEY)), executor).get();

            assertThat(seen).isNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
