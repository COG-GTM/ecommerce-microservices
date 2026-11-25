package com.ibatulanand.tillservice.payment;

/** Card / contactless PIN pad. The demo uses {@link SimulatedPaymentTerminal}; a real terminal adapter replaces it. */
public interface PaymentTerminal {

    record Authorisation(boolean approved, String authCode, String declineReason) {
    }

    /**
     * @param requestedOutcome demo-only hint from the "Simulate approved/declined" buttons; real terminals ignore it
     */
    Authorisation authorise(String basketId, String tenderType, int amountPence, String requestedOutcome);
}
