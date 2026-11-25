package com.ibatulanand.apigateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window request counter per client, kept in memory on this gateway instance.
 */
public class OrderRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final int maxTrackedClients;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public OrderRateLimiter(int maxRequests, Duration window, int maxTrackedClients, Clock clock) {
        if (maxRequests < 1 || window.isNegative() || window.isZero() || maxTrackedClients < 1) {
            throw new IllegalArgumentException("maxRequests, window and maxTrackedClients must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.maxTrackedClients = maxTrackedClients;
        this.clock = clock;
    }

    public boolean tryAcquire(String clientKey) {
        long now = clock.millis();
        if (windows.size() >= maxTrackedClients && !windows.containsKey(clientKey)) {
            windows.values().removeIf(window -> now - window.start >= windowMillis);
            if (windows.size() >= maxTrackedClients) {
                return false;
            }
        }
        Window window = windows.compute(clientKey, (key, current) ->
                current == null || now - current.start >= windowMillis ? new Window(now, 1) : current.increment());
        return window.count <= maxRequests;
    }

    public long retryAfterSeconds(String clientKey) {
        Window window = windows.get(clientKey);
        long remaining = window == null ? windowMillis : window.start + windowMillis - clock.millis();
        return Math.max(1, (remaining + 999) / 1000);
    }

    private record Window(long start, int count) {
        Window increment() {
            return new Window(start, count == Integer.MAX_VALUE ? count : count + 1);
        }
    }
}
