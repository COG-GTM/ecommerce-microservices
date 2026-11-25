package com.ibatulanand.tillservice.domain;

/** One VAT rate band of a basket or receipt, in integer pence. grossPence = netPence + vatPence. */
public record VatBand(String code, int ratePercent, int netPence, int vatPence, int grossPence) {
}
