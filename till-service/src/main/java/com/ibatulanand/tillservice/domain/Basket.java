package com.ibatulanand.tillservice.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Basket {

    public enum Status { OPEN, COMPLETED, VOIDED }

    private String basketId;
    private String tillId;
    private String transactionNumber;
    private Status status = Status.OPEN;
    private String currency = "GBP";
    private List<BasketLine> lines = new ArrayList<>();
    private List<Adjustment> adjustments = new ArrayList<>();
    /** Most recently added/changed line; the till highlights it. */
    private String lastLineId;
    private int itemCount;
    private int subtotalPence;
    private int savingsPence;
    private int totalPence;
    private String orderNumber;

    /** Age-restricted lines still waiting for a Challenge 25 decision; payment is blocked while this is above 0. */
    public int getPendingAgeChecks() {
        return (int) lines.stream().filter(l -> l.getAgeCheck() == BasketLine.AgeCheck.PENDING).count();
    }
}
