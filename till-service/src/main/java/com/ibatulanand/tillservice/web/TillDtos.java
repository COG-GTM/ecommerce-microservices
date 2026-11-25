package com.ibatulanand.tillservice.web;

import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.VatBand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response shapes of the /api/till contract (see docs/morrisons-till/ARCHITECTURE.md). */
public final class TillDtos {

    private TillDtos() {
    }

    public record Operator(String id, String name) {
    }

    public record TillSession(String storeNumber, String storeName, String tillId, Operator operator,
                              String currency, String transactionNumber, String basketId, boolean backendConnected,
                              Map<String, String> services) {
    }

    public record AddLineRequest(@NotBlank String basketId, @NotBlank String barcode, @Min(1) Integer quantity) {
    }

    public record UpdateLineRequest(@NotBlank String basketId, @Min(1) int quantity) {
    }

    public record VoidSaleRequest(@NotBlank String basketId) {
    }

    public record TenderRequest(@NotBlank String basketId, @NotBlank String tenderType, Integer amountTenderedPence,
                                String terminalOutcome, Integer expectedTotalPence) {
    }

    public record Receipt(String receiptNumber, String storeNumber, String storeName, String tillId,
                          String operatorName, String transactionNumber, String orderNumber, List<BasketLine> lines,
                          int subtotalPence, int savingsPence, int totalPence, String tenderType,
                          int amountTenderedPence, int changeDuePence, String authCode, Instant issuedAt,
                          List<VatBand> vatSummary) {
    }

    public record TenderResult(String status, String tenderType, Basket basket, String orderNumber,
                               int amountTenderedPence, int changeDuePence, String authCode, String declineReason,
                               Receipt receipt, String nextBasketId) {
    }
}
