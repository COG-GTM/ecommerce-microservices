package com.ibatulanand.tillservice.domain;

/**
 * One payment taken against a basket. {@code amountPence} is what it paid off the balance; for cash,
 * {@code tenderedPence} is what the customer handed over and {@code changeDuePence} what the till gave back.
 */
public record TenderLine(String tenderType, int amountPence, int tenderedPence, int changeDuePence, String authCode) {
}
