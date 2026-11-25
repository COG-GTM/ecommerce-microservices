package com.ibatulanand.tillservice.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Request/response shapes of the /api/till/refund contract: return items against a completed sale's receipt. */
public final class RefundDtos {

    private RefundDtos() {
    }

    /** One line of a completed sale as seen in Refund mode (order-service's view, enriched with the product name). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundableLine(String skuCode, String name, int unitPricePence, int soldQuantity,
                                 int refundedQuantity, int refundableQuantity) {
        public RefundableLine withName(String productName) {
            return new RefundableLine(skuCode, productName, unitPricePence, soldQuantity, refundedQuantity,
                    refundableQuantity);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundableSale(String orderNumber, String transactionId, String storeNumber, String tillId,
                                 String tenderType, int totalPence, int refundedPence, int refundablePence,
                                 List<RefundableLine> lines, List<String> refunds) {
    }

    public record RefundLineRequest(@NotBlank String skuCode, @NotNull @Min(1) Integer quantity) {
    }

    public record RefundRequest(String tillId, @NotBlank String reference,
                                @NotEmpty List<@NotNull @Valid RefundLineRequest> lines) {
    }

    /** What order-service persisted for the refund: a negative order linked to the sale. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundOrder(String orderNumber, String originalOrderNumber, String transactionId,
                              Integer totalPence) {
    }

    public record RefundedLine(String skuCode, String name, int quantity, int unitPricePence, int lineTotalPence) {
    }

    public record RefundResult(String status, String refundNumber, String originalOrderNumber,
                               String transactionNumber, String tillId, String operatorName, String tenderType,
                               int refundPence, List<RefundedLine> lines, RefundableSale remaining,
                               Instant issuedAt) {
    }
}
