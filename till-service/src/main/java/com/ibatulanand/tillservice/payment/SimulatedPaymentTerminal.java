package com.ibatulanand.tillservice.payment;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SimulatedPaymentTerminal implements PaymentTerminal {

    @Override
    public Authorisation authorise(String basketId, String tenderType, int amountPence, String requestedOutcome) {
        if ("DECLINED".equalsIgnoreCase(requestedOutcome)) {
            return new Authorisation(false, null, "Card declined by issuer");
        }
        String authCode = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return new Authorisation(true, authCode, null);
    }
}
