package com.ibatulanand.tillservice.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Request/response shapes of the /api/till/refund contract: return items against a completed sale's receipt. */
public final class RefundDtos {

    private RefundDtos() {
    }

    /**
     * One line of a completed sale as seen in Refund mode (order-service's view, enriched with the product name).
     * paidPence is what the customer paid for its soldQuantity units after savings; unitPricePence is paidPence per
     * unit, or null when that is not a whole number of pence.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundableLine(Long lineId, String skuCode, String name, Integer unitPricePence, int paidPence,
                                 int soldQuantity, int refundedQuantity, int refundableQuantity) {
        public RefundableLine withName(String productName) {
            return new RefundableLine(lineId, skuCode, productName, unitPricePence, paidPence, soldQuantity,
                    refundedQuantity, refundableQuantity);
        }

        /** Same split as order-service: an even share of paidPence, the leftover pennies going to the last units. */
        public int refundPenceFor(int quantity) {
            return share(refundedQuantity + quantity) - share(refundedQuantity);
        }

        /** The next quantity units grouped into runs of one unit price, as order-service writes the refund lines. */
        public List<RefundedLine> refundRuns(int quantity) {
            List<RefundedLine> runs = new ArrayList<>();
            for (int unit = refundedQuantity + 1; unit <= refundedQuantity + quantity; unit++) {
                int pence = share(unit) - share(unit - 1);
                RefundedLine last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                if (last != null && last.unitPricePence() == pence) {
                    runs.set(runs.size() - 1, new RefundedLine(lineId, skuCode, name, last.quantity() + 1, pence,
                            last.lineTotalPence() + pence));
                } else {
                    runs.add(new RefundedLine(lineId, skuCode, name, 1, pence, pence));
                }
            }
            return runs;
        }

        private int share(int units) {
            return soldQuantity == 0 ? 0 : (int) ((long) paidPence * Math.min(units, soldQuantity) / soldQuantity);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundableSale(String orderNumber, String transactionId, String storeNumber, String tillId,
                                 String tenderType, int totalPence, int refundedPence, int refundablePence,
                                 List<RefundableLine> lines, List<String> refunds) {
    }

    /** lineId picks one line of the sale (a weighed pack); without it the quantity comes off the SKU's lines in order. */
    public record RefundLineRequest(@NotBlank String skuCode, @NotNull @Min(1) Integer quantity, Long lineId) {
        public RefundLineRequest(String skuCode, Integer quantity) {
            this(skuCode, quantity, null);
        }
    }

    public record RefundRequest(String tillId, @NotBlank String reference,
                                @NotEmpty List<@NotNull @Valid RefundLineRequest> lines) {
    }

    /** What order-service persisted for the refund: a negative order linked to the sale. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundOrder(String orderNumber, String originalOrderNumber, String transactionId,
                              Integer totalPence) {
    }

    /** unitPricePence x quantity is exactly lineTotalPence; one sale line can come back as two runs a penny apart. */
    public record RefundedLine(Long lineId, String skuCode, String name, int quantity, int unitPricePence,
                               int lineTotalPence) {
    }

    public record RefundResult(String status, String refundNumber, String originalOrderNumber,
                               String transactionNumber, String tillId, String operatorName, String tenderType,
                               int refundPence, List<RefundedLine> lines, RefundableSale remaining,
                               Instant issuedAt) {
    }
}
