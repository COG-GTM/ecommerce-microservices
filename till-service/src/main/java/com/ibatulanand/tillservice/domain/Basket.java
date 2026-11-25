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
    /** Payments taken so far; more than one line is a split tender. */
    private List<TenderLine> tenders = new ArrayList<>();

    public int getPaidPence() {
        return tenders.stream().mapToInt(TenderLine::amountPence).sum();
    }

    public int getBalanceDuePence() {
        return Math.max(0, totalPence - getPaidPence());
    }
}
