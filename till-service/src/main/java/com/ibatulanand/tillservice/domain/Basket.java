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
}
