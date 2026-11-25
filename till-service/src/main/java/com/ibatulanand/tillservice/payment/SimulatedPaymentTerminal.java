package com.ibatulanand.tillservice.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PIN pad simulator: approved, declined, or timeout after a configurable delay.
 * <p>
 * A timeout models the risky case: the terminal took the payment but its response never reached the till. The charge
 * is remembered against the idempotency key, so a retry with the same key gets that approval back instead of
 * charging the card a second time.
 */
@Component
@Slf4j
public class SimulatedPaymentTerminal implements PaymentTerminal {

    static final int MAX_REMEMBERED_KEYS = 10_000;
    public static final String DECLINE_REASON = "Card declined by issuer";
    public static final String TIMEOUT_REASON = "PIN pad did not respond";

    private final long timeoutDelayMs;
    private final AtomicInteger charges = new AtomicInteger();
    private final Map<String, Charge> chargesByKey = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Charge> eldest) {
                    return size() > MAX_REMEMBERED_KEYS;
                }
            });

    record Charge(Authorisation authorisation, String basketId, int amountPence) {
    }

    public SimulatedPaymentTerminal(@Value("${till.terminal.simulator.timeout-delay-ms:1500}") long timeoutDelayMs) {
        this.timeoutDelayMs = timeoutDelayMs;
    }

    @Override
    public Authorisation authorise(String idempotencyKey, String basketId, String tenderType, int amountPence,
                                   String requestedOutcome) {
        Charge previous = chargesByKey.get(idempotencyKey);
        if (previous != null && previous.basketId().equals(basketId) && previous.amountPence() == amountPence) {
            log.info("Terminal replayed attempt {} ({}), no new charge", idempotencyKey, previous.authorisation().outcome());
            return previous.authorisation();
        }
        if ("DECLINED".equalsIgnoreCase(requestedOutcome)) {
            Authorisation declined = Authorisation.declined(DECLINE_REASON);
            chargesByKey.put(idempotencyKey, new Charge(declined, basketId, amountPence));
            return declined;
        }
        Authorisation approved = Authorisation.approved(
                String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        chargesByKey.put(idempotencyKey, new Charge(approved, basketId, amountPence));
        charges.incrementAndGet();
        if ("TIMEOUT".equalsIgnoreCase(requestedOutcome)) {
            sleep(timeoutDelayMs);
            log.info("Terminal timed out after {}ms on attempt {} (charge taken, response lost)", timeoutDelayMs,
                    idempotencyKey);
            return Authorisation.timedOut(TIMEOUT_REASON);
        }
        return approved;
    }

    /** Cards actually charged since start-up. */
    public int chargeCount() {
        return charges.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
