package com.ibatulanand.notificationservice.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory record of notifications already sent, keyed on {@code orderNumber} + notification type,
 * so a redelivered event does not notify twice. Bounded by {@code notification.dedupe.max-entries}
 * and {@code notification.dedupe.ttl}.
 */
@Slf4j
@Component
public class NotificationDeduplicator {

    private final Map<String, Instant> sent = new LinkedHashMap<>();
    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public NotificationDeduplicator(@Value("${notification.dedupe.max-entries:100000}") int maxEntries,
                                    @Value("${notification.dedupe.ttl:PT24H}") Duration ttl) {
        this(maxEntries, ttl, Clock.systemUTC());
    }

    NotificationDeduplicator(int maxEntries, Duration ttl, Clock clock) {
        this.maxEntries = maxEntries;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Runs {@code send} unless a notification with the same key was already sent. If {@code send}
     * throws, the key is released so a retry can send it.
     *
     * @return {@code true} if {@code send} ran
     */
    public boolean sendOnce(String orderNumber, String notificationKey, Runnable send) {
        String key = orderNumber + "|" + notificationKey;
        if (!claim(key)) {
            log.info("Skipping duplicate {} for order {}", notificationKey, orderNumber);
            return false;
        }
        try {
            send.run();
            return true;
        } catch (RuntimeException e) {
            release(key);
            throw e;
        }
    }

    private synchronized boolean claim(String key) {
        Instant now = clock.instant();
        evictExpired(now);
        if (sent.containsKey(key)) {
            return false;
        }
        Iterator<String> oldest = sent.keySet().iterator();
        while (sent.size() >= maxEntries && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        sent.put(key, now);
        return true;
    }

    private synchronized void release(String key) {
        sent.remove(key);
    }

    private void evictExpired(Instant now) {
        Instant cutoff = now.minus(ttl);
        Iterator<Map.Entry<String, Instant>> it = sent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> oldest = it.next();
            if (oldest.getValue().isBefore(cutoff)) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
