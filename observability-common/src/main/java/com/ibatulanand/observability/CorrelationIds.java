package com.ibatulanand.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private CorrelationIds() {
    }

    /**
     * Returns the incoming correlation ID when it is safe to log and propagate,
     * otherwise a newly generated one.
     */
    public static String resolve(String incoming) {
        if (incoming != null && VALID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
