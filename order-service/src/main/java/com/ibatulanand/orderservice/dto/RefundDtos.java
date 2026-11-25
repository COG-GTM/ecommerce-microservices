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

    /** paidPence is what the customer paid for all soldQuantity units of the SKU, after savings. */
    public record RefundableLine(String skuCode, String name, int unitPricePence, int paidPence, int soldQuantity,
                                 int refundedQuantity, int refundableQuantity) {

        /** Money back for the next quantity units: an even share of paidPence, the pennies left over going to the last units. */
        public int refundPenceFor(int quantity) {
            return share(refundedQuantity + quantity) - share(refundedQuantity);
        }

        private int share(int units) {
            return soldQuantity == 0 ? 0 : (int) ((long) paidPence * Math.min(units, soldQuantity) / soldQuantity);
        }
    }

    /** A completed sale with what has already been refunded against it and what is still refundable. */
    public record RefundableSale(String orderNumber, String transactionId, String storeNumber, String tillId,
                                 String tenderType, int totalPence, int refundedPence, int refundablePence,
                                 List<RefundableLine> lines, List<String> refunds) {
    }
}
