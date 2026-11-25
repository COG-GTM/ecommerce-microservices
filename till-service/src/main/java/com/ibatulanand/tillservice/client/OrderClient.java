package com.ibatulanand.tillservice.client;

import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.web.RefundDtos.RefundLineRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundOrder;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableSale;

import java.util.List;

/** Persists a completed sale (order-service), which publishes the order event to Kafka. */
public interface OrderClient {

    /** @return the order number assigned by order-service */
    String placeSale(Basket basket, String storeNumber, String tenderType);

    /** @param reference order number, receipt number or transaction number of a completed sale */
    RefundableSale refundable(String reference);

    /** Persists a negative order linked to the sale; order-service rejects quantities beyond what is left. */
    RefundOrder refund(String reference, String storeNumber, String tillId, String transactionId,
                       List<RefundLineRequest> lines);

    /**
     * Same as {@link #placeSale(Basket, String, String)}, but order-service stores at most one order per
     * idempotency key, so replaying a queued offline sale returns the order already stored.
     */
    default String placeSale(Basket basket, String storeNumber, String tenderType, String idempotencyKey) {
        return placeSale(basket, storeNumber, tenderType);
    }
}
