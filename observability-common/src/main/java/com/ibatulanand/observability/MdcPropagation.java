package com.ibatulanand.observability;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries the caller's MDC (correlation and trace IDs) onto the thread that runs an async task.
 */
public final class MdcPropagation {

    private MdcPropagation() {
    }

    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setContext(callerContext);
            try {
                return task.get();
            } finally {
                setContext(previous);
            }
        };
    }

    private static void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
