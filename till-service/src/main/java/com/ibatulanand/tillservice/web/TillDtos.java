package com.ibatulanand.tillservice.web;

import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.VatBand;
import jakarta.validation.constraints.Max;
import com.ibatulanand.tillservice.domain.TenderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response shapes of the /api/till contract (see docs/morrisons-till/ARCHITECTURE.md). */
public final class TillDtos {

    /** Largest weight the till scale accepts (30 kg). */
    public static final int MAX_WEIGHT_GRAMS = 30_000;

    private TillDtos() {
    }

    public record Operator(String id, String name) {
    }

    public record TillSession(String storeNumber, String storeName, String tillId, Operator operator,
                              String currency, String transactionNumber, String basketId, boolean backendConnected,
                              Map<String, String> services) {
    }

    public record AddLineRequest(@NotBlank String basketId, @NotBlank String barcode, @Min(1) Integer quantity,
                                 @Min(1) @Max(MAX_WEIGHT_GRAMS) Integer weightGrams) {

        public AddLineRequest(String basketId, String barcode, Integer quantity) {
            this(basketId, barcode, quantity, null);
        }
    }

    public record UpdateLineRequest(@NotBlank String basketId, @Min(1) int quantity) {
    }

    public record AgeCheckRequest(@NotBlank String basketId, @NotBlank String decision) {
    }

    public record VoidSaleRequest(@NotBlank String basketId) {
    }

    public record TenderRequest(@NotBlank String basketId, @NotBlank String tenderType, Integer amountTenderedPence,
                                String terminalOutcome, Integer expectedTotalPence, Boolean partialPayment,
                                String idempotencyKey, Integer expectedBalanceDuePence) {
    }

    /** Server-side cash preview: change due, or the balance left for another tender when the cash is short. */
    public record CashQuote(int totalPence, int paidPence, int balanceDuePence, int amountTenderedPence,
                            int changeDuePence, int remainingPence) {
    }

    public record Receipt(String receiptNumber, String storeNumber, String storeName, String tillId,
                          String operatorName, String transactionNumber, String orderNumber, List<BasketLine> lines,
                          int subtotalPence, int savingsPence, int totalPence, String tenderType,
                          int amountTenderedPence, int changeDuePence, String authCode, Instant issuedAt,
                          List<VatBand> vatSummary,
                          MoreCardReceipt moreCard,
                          List<TenderLine> tenders) {
    }

    /** More Card section of the receipt; null for a non-member sale. */
    public record MoreCardReceipt(String cardLast4, String memberName, int pointsEarned, int pointsBalance) {
    }

    public record AttachMemberRequest(@NotBlank String basketId, @NotBlank String cardNumber) {
    }

    /** A line of a sale the till completed while offline; name/sku/price are the till's cached catalogue values. */
    public record OfflineSaleLine(@NotBlank String barcode, String skuCode, String name, @Min(1) int quantity,
                                  Integer unitPricePence) {
    }

    /** A cash sale completed while the till was offline, replayed on reconnect. {@code saleId} is the idempotency key. */
    public record OfflineSaleRequest(@NotBlank String saleId, String tillId, String transactionNumber,
                                     @NotBlank String tenderType, @NotEmpty @Size(max = 200) List<@Valid OfflineSaleLine> lines,
                                     Integer amountTenderedPence, Integer totalPence, Instant completedAt) {
    }

    public record OfflineSaleResult(String saleId, String orderNumber, String transactionNumber, int totalPence,
                                    int amountTenderedPence) {
    }

    public record TenderResult(String status, String tenderType, Basket basket, String orderNumber,
                               int amountTenderedPence, int changeDuePence, String authCode, String declineReason,
                               Receipt receipt, String nextBasketId, int balanceDuePence,
                               String idempotencyKey) {
    }
}
