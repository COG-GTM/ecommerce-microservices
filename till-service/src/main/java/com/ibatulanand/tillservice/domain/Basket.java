package com.ibatulanand.tillservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibatulanand.tillservice.loyalty.MoreCardMember;
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
    /** VAT breakdown per rate band; grossPence across bands sums to totalPence. */
    private List<VatBand> vatSummary = new ArrayList<>();
    private String orderNumber;
    /** More Card holder attached to this transaction, or null for a non-member sale. */
    private MoreCardMember member;
    @JsonIgnore
    private String memberCardNumber;

    /** Age-restricted lines still waiting for a Challenge 25 decision; payment is blocked while this is above 0. */
    public int getPendingAgeChecks() {
        return (int) lines.stream().filter(l -> l.getAgeCheck() == BasketLine.AgeCheck.PENDING).count();
    }

    /** Payments taken so far; more than one line is a split tender. */
    private List<TenderLine> tenders = new ArrayList<>();

    public int getPaidPence() {
        return tenders.stream().mapToInt(TenderLine::amountPence).sum();
    }

    public int getBalanceDuePence() {
        return Math.max(0, totalPence - getPaidPence());
    }
}
