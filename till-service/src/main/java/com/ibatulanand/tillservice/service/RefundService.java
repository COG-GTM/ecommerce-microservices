package com.ibatulanand.tillservice.service;

import com.ibatulanand.tillservice.client.CatalogueClient;
import com.ibatulanand.tillservice.client.OrderClient;
import com.ibatulanand.tillservice.config.TillProperties;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.web.RefundDtos.RefundLineRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundOrder;
import com.ibatulanand.tillservice.web.RefundDtos.RefundRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundResult;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableLine;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableSale;
import com.ibatulanand.tillservice.web.RefundDtos.RefundedLine;
import com.ibatulanand.tillservice.web.TillException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refund mode: look up a completed sale by receipt/order number and return items against it. order-service is the
 * source of truth for what was sold and already refunded (and enforces the limit transactionally); the till checks
 * the same limit first so the operator gets an immediate, specific error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final OrderClient orders;
    private final CatalogueClient catalogue;
    private final TillProperties properties;

    private final Map<String, AtomicInteger> refundCounters = new ConcurrentHashMap<>();

    public RefundableSale lookup(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Enter a receipt or order number");
        }
        return withNames(orders.refundable(reference.trim()));
    }

    public RefundResult refund(RefundRequest request) {
        String tillId = request.tillId() == null || request.tillId().isBlank()
                ? properties.defaultTillId() : request.tillId();
        RefundableSale sale = lookup(request.reference());

        Map<String, Integer> requested = new LinkedHashMap<>();
        request.lines().forEach(l -> requested.merge(l.skuCode(), l.quantity(), Integer::sum));
        Map<String, RefundableLine> bySku = sale.lines().stream()
                .collect(Collectors.toMap(RefundableLine::skuCode, Function.identity(), (a, b) -> a));
        List<RefundedLine> refunded = new ArrayList<>();
        int refundPence = 0;
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            RefundableLine line = bySku.get(entry.getKey());
            if (line == null) {
                throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_REFUND",
                        entry.getKey() + " was not sold on this receipt");
            }
            if (entry.getValue() > line.refundableQuantity()) {
                throw new TillException(HttpStatus.CONFLICT, "REFUND_EXCEEDS_SOLD",
                        "Cannot refund " + entry.getValue() + " x " + displayName(line) + ": " + line.soldQuantity()
                                + " sold, " + line.refundedQuantity() + " already refunded");
            }
            int lineTotal = line.unitPricePence() * entry.getValue();
            refunded.add(new RefundedLine(line.skuCode(), line.name(), entry.getValue(), line.unitPricePence(),
                    lineTotal));
            refundPence += lineTotal;
        }

        String transactionNumber = nextTransactionNumber(tillId);
        List<RefundLineRequest> lines = requested.entrySet().stream()
                .map(e -> new RefundLineRequest(e.getKey(), e.getValue())).toList();
        RefundOrder order = orders.refund(sale.orderNumber(), properties.storeNumber(), tillId, transactionNumber,
                lines);
        int persistedPence = order.totalPence() == null ? refundPence : Math.abs(order.totalPence());
        log.info("Till {} refunded {}p against order {} as {}", tillId.replaceAll("[\\r\\n]", "_"), persistedPence,
                sale.orderNumber(), order.orderNumber());
        RefundableSale remaining;
        try {
            remaining = lookup(sale.orderNumber());
        } catch (RuntimeException e) {
            // The refund is committed; never report it as failed because the refreshed view could not be read.
            log.warn("Refund {} committed but the refreshed sale could not be read", order.orderNumber(), e);
            remaining = null;
        }
        return new RefundResult("REFUNDED", order.orderNumber(), sale.orderNumber(), transactionNumber, tillId,
                properties.operatorName(), sale.tenderType(), persistedPence, refunded, remaining, Instant.now());
    }

    private RefundableSale withNames(RefundableSale sale) {
        Map<String, String> names;
        try {
            names = catalogue.quickKeys().stream()
                    .collect(Collectors.toMap(CatalogueItem::skuCode, CatalogueItem::name, (a, b) -> a));
        } catch (RuntimeException e) {
            names = Map.of();
        }
        Map<String, String> known = names;
        List<RefundableLine> lines = sale.lines() == null ? List.of() : sale.lines().stream()
                .map(l -> l.withName(known.getOrDefault(l.skuCode(), l.name() != null ? l.name() : l.skuCode())))
                .toList();
        return new RefundableSale(sale.orderNumber(), sale.transactionId(), sale.storeNumber(), sale.tillId(),
                sale.tenderType(), sale.totalPence(), sale.refundedPence(), sale.refundablePence(), lines,
                sale.refunds() == null ? List.of() : sale.refunds());
    }

    private String nextTransactionNumber(String tillId) {
        int n = refundCounters.computeIfAbsent(tillId, k -> new AtomicInteger()).incrementAndGet();
        return String.format("%s-%s-R%04d", properties.storeNumber(), tillId, n);
    }

    private static String displayName(RefundableLine line) {
        return line.name() != null ? line.name() : line.skuCode();
    }
}
