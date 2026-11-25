package com.ibatulanand.orderservice.dto;

import java.util.List;

/** Shapes of the till refund endpoints: return items from a completed sale, at most what was sold across refunds. */
public final class RefundDtos {

    private RefundDtos() {
    }

    public record RefundLineRequest(String skuCode, Integer quantity) {
    }

    public record RefundRequest(String storeNumber, String tillId, String transactionId,
                                List<RefundLineRequest> lines) {
    }

    public record RefundableLine(String skuCode, int unitPricePence, int soldQuantity, int refundedQuantity,
                                 int refundableQuantity) {
    }

    /** A completed sale with what has already been refunded against it and what is still refundable. */
    public record RefundableSale(String orderNumber, String transactionId, String storeNumber, String tillId,
                                 String tenderType, int totalPence, int refundedPence, int refundablePence,
                                 List<RefundableLine> lines, List<String> refunds) {
    }
}
