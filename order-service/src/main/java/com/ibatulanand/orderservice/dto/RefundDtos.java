package com.ibatulanand.orderservice.dto;

import java.util.ArrayList;
import java.util.List;

/** Shapes of the till refund endpoints: return items from a completed sale, at most what was sold across refunds. */
public final class RefundDtos {

    private RefundDtos() {
    }

    /** lineId picks one line of the sale (a weighed pack); without it the quantity comes off the SKU's lines in order. */
    public record RefundLineRequest(String skuCode, Integer quantity, Long lineId) {
        public RefundLineRequest(String skuCode, Integer quantity) {
            this(skuCode, quantity, null);
        }
    }

    public record RefundRequest(String storeNumber, String tillId, String transactionId,
                                List<RefundLineRequest> lines) {
    }

    /**
     * One line of the sale. paidPence is what the customer paid for its soldQuantity units after savings;
     * unitPricePence is paidPence per unit, or null when that is not a whole number of pence.
     */
    public record RefundableLine(Long lineId, String skuCode, String name, Integer unitPricePence, int paidPence,
                                 int soldQuantity, int refundedQuantity, int refundableQuantity) {

        /** Money back for the next quantity units: an even share of paidPence, the pennies left over going to the last units. */
        public int refundPenceFor(int quantity) {
            return share(refundedQuantity + quantity) - share(refundedQuantity);
        }

        /** The next quantity units grouped into runs of one unit price, so each run's price x quantity is exact. */
        public List<Run> refundRuns(int quantity) {
            List<Run> runs = new ArrayList<>();
            for (int unit = refundedQuantity + 1; unit <= refundedQuantity + quantity; unit++) {
                int pence = share(unit) - share(unit - 1);
                if (!runs.isEmpty() && runs.get(runs.size() - 1).unitPence() == pence) {
                    runs.set(runs.size() - 1, new Run(runs.get(runs.size() - 1).quantity() + 1, pence));
                } else {
                    runs.add(new Run(1, pence));
                }
            }
            return runs;
        }

        private int share(int units) {
            return soldQuantity == 0 ? 0 : (int) ((long) paidPence * Math.min(units, soldQuantity) / soldQuantity);
        }

        public static Integer exactUnitPence(int paidPence, int quantity) {
            return quantity > 0 && paidPence % quantity == 0 ? paidPence / quantity : null;
        }
    }

    public record Run(int quantity, int unitPence) {
    }

    /** A completed sale with what has already been refunded against it and what is still refundable. */
    public record RefundableSale(String orderNumber, String transactionId, String storeNumber, String tillId,
                                 String tenderType, int totalPence, int refundedPence, int refundablePence,
                                 List<RefundableLine> lines, List<String> refunds) {
    }
}
