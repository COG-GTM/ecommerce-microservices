package com.ibatulanand.notificationservice.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDeduplicatorTest {

    private final AtomicInteger sends = new AtomicInteger();

    @Test
    void sendsOncePerOrderAndType() {
        NotificationDeduplicator dedupe = new NotificationDeduplicator(100, Duration.ofHours(1));

        assertThat(dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet)).isTrue();
        assertThat(dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet)).isFalse();
        assertThat(dedupe.sendOnce("O1", "SHIP_NOTICE", sends::incrementAndGet)).isTrue();
        assertThat(dedupe.sendOnce("O2", "RECEIPT", sends::incrementAndGet)).isTrue();
        assertThat(sends).hasValue(3);
    }

    @Test
    void failedSendCanBeRetried() {
        NotificationDeduplicator dedupe = new NotificationDeduplicator(100, Duration.ofHours(1));

        assertThatThrownBy(() -> dedupe.sendOnce("O1", "RECEIPT", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet)).isTrue();
    }

    @Test
    void expiredAndOverflowEntriesAreEvicted() {
        MutableClock clock = new MutableClock();
        NotificationDeduplicator dedupe = new NotificationDeduplicator(2, Duration.ofMinutes(10), clock);
        dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet);
        clock.advance(Duration.ofMinutes(11));
        assertThat(dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet)).isTrue();

        dedupe.sendOnce("O2", "RECEIPT", sends::incrementAndGet);
        dedupe.sendOnce("O3", "RECEIPT", sends::incrementAndGet);
        assertThat(dedupe.sendOnce("O1", "RECEIPT", sends::incrementAndGet)).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-16T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
