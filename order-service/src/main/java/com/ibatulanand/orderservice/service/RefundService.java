package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.RefundDtos.RefundLineRequest;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundRequest;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundableLine;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundableSale;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refunds against a completed sale. A refund is persisted as its own order (type REFUND, negative quantities and
 * total) linked to the sale, and published on notificationTopic as a REFUND event. The original sale row is locked
 * while a refund is validated, so concurrent refunds can never return more than was sold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    public static final String SALE = "SALE";
    public static final String REFUND = "REFUND";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    /** @param reference order number, receipt number (R-&lt;transaction&gt;) or transaction number of the sale */
    @Transactional(readOnly = true)
    public RefundableSale refundable(String reference) {
        return summarise(resolveSale(reference));
    }

    @Transactional
    public Order refund(String reference, RefundRequest request) {
        String orderNumber = resolveSale(reference).getOrderNumber();
        Order sale = orderRepository.findWithLockByOrderNumber(orderNumber).orElseThrow(() -> unknown(reference));
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND", "Select at least one item to refund");
        }
        RefundableSale summary = summarise(sale);
        Map<String, RefundableLine> bySku = new LinkedHashMap<>();
        summary.lines().forEach(l -> bySku.put(l.skuCode(), l));

        Map<String, Integer> requested = new LinkedHashMap<>();
        for (RefundLineRequest line : request.lines()) {
            if (line == null || line.skuCode() == null || line.quantity() == null || line.quantity() < 1) {
                throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND", "Refund quantities must be at least 1");
            }
            if (!bySku.containsKey(line.skuCode())) {
                throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND",
                        line.skuCode() + " was not sold on order " + sale.getOrderNumber());
            }
            requested.merge(line.skuCode(), line.quantity(), Integer::sum);
        }

        List<OrderLineItems> refundLines = new ArrayList<>();
        int refundPence = 0;
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            RefundableLine available = bySku.get(entry.getKey());
            if (entry.getValue() > available.refundableQuantity()) {
                throw new RefundException(HttpStatus.CONFLICT, "REFUND_EXCEEDS_SOLD",
                        "Cannot refund " + entry.getValue() + " x " + entry.getKey() + ": " + available.soldQuantity()
                                + " sold, " + available.refundedQuantity() + " already refunded");
            }
            OrderLineItems item = new OrderLineItems();
            item.setSkuCode(entry.getKey());
            item.setPrice(BigDecimal.valueOf(available.unitPricePence(), 2));
            item.setQuantity(-entry.getValue());
            refundLines.add(item);
            refundPence += available.unitPricePence() * entry.getValue();
        }
        // Never refund more money than the sale took (e.g. if the sale had basket-level savings).
        refundPence = Math.min(refundPence, summary.refundablePence());

        Order refund = new Order();
        refund.setOrderNumber(UUID.randomUUID().toString());
        refund.setOrderType(REFUND);
        refund.setOriginalOrderNumber(sale.getOrderNumber());
        refund.setChannel(sale.getChannel());
        refund.setStoreNumber(request.storeNumber() != null ? request.storeNumber() : sale.getStoreNumber());
        refund.setTillId(request.tillId() != null ? request.tillId() : sale.getTillId());
        refund.setTransactionId(request.transactionId());
        refund.setTenderType(request.tenderType() != null ? request.tenderType() : sale.getTenderType());
        refund.setTotalPence(-refundPence);
        refund.setOrderLineItemsList(refundLines);
        orderRepository.save(refund);

        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(refund.getOrderNumber(), refund.getChannel(),
                refund.getStoreNumber(), refund.getTillId(), refund.getTransactionId(), refund.getTenderType(),
                refund.getTotalPence(), REFUND, sale.getOrderNumber()));
        log.info("Refund {} of {}p against order {}", refund.getOrderNumber(), refundPence, sale.getOrderNumber());
        return refund;
    }

    private Order resolveSale(String reference) {
        if (reference == null || reference.isBlank()) {
            throw unknown(reference);
        }
        String ref = reference.trim();
        Order order = orderRepository.findFirstByOrderNumber(ref).orElseGet(() -> {
            String transactionId = ref.regionMatches(true, 0, "R-", 0, 2) ? ref.substring(2) : ref;
            return orderRepository.findByTransactionIdOrderByIdDesc(transactionId).stream()
                    .filter(o -> !REFUND.equals(o.getOrderType())).findFirst().orElse(null);
        });
        if (order == null) {
            throw unknown(reference);
        }
        if (REFUND.equals(order.getOrderType())) {
            throw new RefundException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_REFUNDABLE",
                    "Order " + order.getOrderNumber() + " is a refund, not a sale");
        }
        return order;
    }

    private RefundableSale summarise(Order sale) {
        Map<String, int[]> soldBySku = new LinkedHashMap<>();
        for (OrderLineItems line : lines(sale)) {
            int[] entry = soldBySku.computeIfAbsent(line.getSkuCode(), k -> new int[]{0, toPence(line.getPrice())});
            entry[0] += line.getQuantity() == null ? 0 : line.getQuantity();
        }
        List<Order> refunds = orderRepository.findByOriginalOrderNumber(sale.getOrderNumber());
        Map<String, Integer> refundedBySku = new LinkedHashMap<>();
        int refundedPence = 0;
        for (Order refund : refunds) {
            refundedPence += refund.getTotalPence() == null ? 0 : -refund.getTotalPence();
            for (OrderLineItems line : lines(refund)) {
                refundedBySku.merge(line.getSkuCode(), Math.abs(line.getQuantity() == null ? 0 : line.getQuantity()),
                        Integer::sum);
            }
        }
        List<RefundableLine> result = new ArrayList<>();
        soldBySku.forEach((sku, entry) -> {
            int refunded = refundedBySku.getOrDefault(sku, 0);
            result.add(new RefundableLine(sku, entry[1], entry[0], refunded, Math.max(0, entry[0] - refunded)));
        });
        int total = sale.getTotalPence() != null ? sale.getTotalPence()
                : result.stream().mapToInt(l -> l.unitPricePence() * l.soldQuantity()).sum();
        return new RefundableSale(sale.getOrderNumber(), sale.getTransactionId(), sale.getStoreNumber(),
                sale.getTillId(), sale.getTenderType(), total, refundedPence, Math.max(0, total - refundedPence),
                result, refunds.stream().map(Order::getOrderNumber).toList());
    }

    private static List<OrderLineItems> lines(Order order) {
        return order.getOrderLineItemsList() == null ? List.of() : order.getOrderLineItemsList();
    }

    private static int toPence(BigDecimal price) {
        return price == null ? 0 : price.movePointRight(2).intValue();
    }

    private static RefundException unknown(String reference) {
        return new RefundException(HttpStatus.NOT_FOUND, "UNKNOWN_ORDER", "No completed sale for " + reference);
    }
}
