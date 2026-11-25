package com.ibatulanand.tillservice.payment;

/**
 * Card / contactless PIN pad adapter. The demo uses {@link SimulatedPaymentTerminal}; a real terminal adapter replaces it.
 * <p>
 * Every call carries the tender attempt's idempotency key. An adapter must charge the card at most once per key: a
 * repeated call with the same key (a retry after a timeout) returns the outcome of the first charge, never a new one.
 */
public interface PaymentTerminal {

    enum Outcome { APPROVED, DECLINED, TIMEOUT }

    record Authorisation(Outcome outcome, String authCode, String declineReason) {

        public static Authorisation approved(String authCode) {
            return new Authorisation(Outcome.APPROVED, authCode, null);
        }

        public static Authorisation declined(String reason) {
            return new Authorisation(Outcome.DECLINED, null, reason);
        }

        public static Authorisation timedOut(String reason) {
            return new Authorisation(Outcome.TIMEOUT, null, reason);
        }

        public boolean approved() {
            return outcome == Outcome.APPROVED;
        }
    }

    /**
     * @param idempotencyKey   one per tender attempt; the same key on a retry must never charge twice
     * @param requestedOutcome demo-only hint (APPROVED, DECLINED, TIMEOUT) from the till's simulate buttons; real
     *                         terminals ignore it
     */
    Authorisation authorise(String idempotencyKey, String basketId, String tenderType, int amountPence,
                            String requestedOutcome);
}
