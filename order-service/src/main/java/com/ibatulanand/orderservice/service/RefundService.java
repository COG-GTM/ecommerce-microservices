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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    // READ_COMMITTED: refund history must be read after the sale lock is granted, not from a snapshot taken before it.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order refund(String reference, RefundRequest request) {
        String orderNumber = resolveSale(reference).getOrderNumber();
        Order sale = orderRepository.findWithLockByOrderNumber(orderNumber).orElseThrow(() -> unknown(reference));
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND", "Select at least one item to refund");
        }
        RefundableSale summary = summarise(sale);
        Map<String, RefundableLine> bySku = new LinkedHashMap<>();
        summary.lines().forEach(l -> bySku.put(l.skuCode(), l));

        Map<String, Long> requested = new LinkedHashMap<>();
        for (RefundLineRequest line : request.lines()) {
            if (line == null || line.skuCode() == null || line.quantity() == null || line.quantity() < 1) {
                throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND", "Refund quantities must be at least 1");
            }
            if (!bySku.containsKey(line.skuCode())) {
                throw new RefundException(HttpStatus.BAD_REQUEST, "INVALID_REFUND",
                        line.skuCode() + " was not sold on order " + sale.getOrderNumber());
            }
            requested.merge(line.skuCode(), (long) line.quantity(), Long::sum);
        }

        List<OrderLineItems> refundLines = new ArrayList<>();
        int refundPence = 0;
        for (Map.Entry<String, Long> entry : requested.entrySet()) {
            RefundableLine available = bySku.get(entry.getKey());
            if (entry.getValue() > available.refundableQuantity()) {
                throw new RefundException(HttpStatus.CONFLICT, "REFUND_EXCEEDS_SOLD",
                        "Cannot refund " + entry.getValue() + " x " + entry.getKey() + ": " + available.soldQuantity()
                                + " sold, " + available.refundedQuantity() + " already refunded");
            }
            int quantity = entry.getValue().intValue();
            int linePence = available.refundPenceFor(quantity);
            OrderLineItems item = new OrderLineItems();
            item.setSkuCode(entry.getKey());
            item.setName(available.name());
            item.setPrice(BigDecimal.valueOf(linePence).divide(BigDecimal.valueOf(quantity * 100L), 2, RoundingMode.HALF_UP));
            item.setQuantity(-quantity);
            item.setPaidPence(-linePence);
            refundLines.add(item);
            refundPence += linePence;
        }
        // Never refund more money than the sale took.
        refundPence = Math.min(refundPence, summary.refundablePence());

        Order refund = new Order();
        refund.setOrderNumber(UUID.randomUUID().toString());
        refund.setOrderType(REFUND);
        refund.setOriginalOrderNumber(sale.getOrderNumber());
        refund.setChannel(sale.getChannel());
        refund.setStoreNumber(request.storeNumber() != null ? request.storeNumber() : sale.getStoreNumber());
        refund.setTillId(request.tillId() != null ? request.tillId() : sale.getTillId());
        refund.setTransactionId(request.transactionId());
        // Money goes back the way it was taken.
        refund.setTenderType(sale.getTenderType());
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
            List<Order> sales = orderRepository.findByTransactionIdOrderByIdDesc(transactionId).stream()
                    .filter(o -> !REFUND.equals(o.getOrderType())).toList();
            if (sales.size() > 1) {
                throw new RefundException(HttpStatus.CONFLICT, "AMBIGUOUS_RECEIPT",
                        "More than one sale has receipt " + ref + "; use the order number");
            }
            return sales.isEmpty() ? null : sales.get(0);
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
        Map<String, Sold> soldBySku = new LinkedHashMap<>();
        for (OrderLineItems line : lines(sale)) {
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            Sold sold = soldBySku.computeIfAbsent(line.getSkuCode(), k -> new Sold());
            sold.quantity += quantity;
            sold.paidPence += line.getPaidPence() != null ? line.getPaidPence() : (long) toPence(line.getPrice()) * quantity;
            if (sold.name == null) {
                sold.name = line.getName();
            }
        }
        capToSaleTotal(soldBySku.values(), sale.getTotalPence());
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
        soldBySku.forEach((sku, sold) -> {
            int refunded = refundedBySku.getOrDefault(sku, 0);
            int paid = (int) sold.paidPence;
            int unit = sold.quantity == 0 ? 0 : Math.round((float) paid / sold.quantity);
            result.add(new RefundableLine(sku, sold.name, unit, paid, sold.quantity, refunded,
                    Math.max(0, sold.quantity - refunded)));
        });
        int total = sale.getTotalPence() != null ? sale.getTotalPence()
                : result.stream().mapToInt(RefundableLine::paidPence).sum();
        return new RefundableSale(sale.getOrderNumber(), sale.getTransactionId(), sale.getStoreNumber(),
                sale.getTillId(), sale.getTenderType(), total, refundedPence, Math.max(0, total - refundedPence),
                result, refunds.stream().map(Order::getOrderNumber).toList());
    }

    private static final class Sold {
        int quantity;
        long paidPence;
        String name;
    }

    /**
     * Lines written without paidPence carry shelf prices, which add up to more than the sale took when it had savings.
     * Scale them down in proportion so that refunding every unit returns exactly the sale total.
     */
    private static void capToSaleTotal(java.util.Collection<Sold> sold, Integer saleTotalPence) {
        long listed = sold.stream().mapToLong(s -> s.paidPence).sum();
        if (saleTotalPence == null || listed <= saleTotalPence || listed == 0) {
            return;
        }
        long assigned = 0;
        for (Sold s : sold) {
            s.paidPence = s.paidPence * saleTotalPence / listed;
            assigned += s.paidPence;
        }
        for (Sold s : sold) {
            if (assigned < saleTotalPence && s.paidPence > 0) {
                s.paidPence++;
                assigned++;
            }
        }
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
