package com.ibatulanand.tillservice.service;

import com.ibatulanand.tillservice.client.CatalogueClient;
import com.ibatulanand.tillservice.client.OrderClient;
import com.ibatulanand.tillservice.client.StockClient;
import com.ibatulanand.tillservice.config.TillProperties;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.payment.PaymentTerminal;
import com.ibatulanand.tillservice.pricing.BasketPricer;
import com.ibatulanand.tillservice.pricing.VatRate;
import com.ibatulanand.tillservice.web.TillDtos.Operator;
import com.ibatulanand.tillservice.web.TillDtos.Receipt;
import com.ibatulanand.tillservice.web.TillDtos.TenderRequest;
import com.ibatulanand.tillservice.web.TillDtos.TenderResult;
import com.ibatulanand.tillservice.web.TillDtos.TillSession;
import com.ibatulanand.tillservice.web.TillException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Till session + basket orchestration. Baskets are in-flight till state held in memory; a sale only becomes
 * durable when it is tendered and persisted through order-service.
 */
@Service
@Slf4j
public class TillService {

    public static final Set<String> TENDER_TYPES = Set.of("CARD", "CONTACTLESS", "CASH");

    private final CatalogueClient catalogue;
    private final StockClient stock;
    private final OrderClient orders;
    private final PaymentTerminal terminal;
    private final BasketPricer pricer;
    private final TillProperties properties;

    static final int MAX_TILLS = 10_000;

    private final Map<String, Basket> baskets = new ConcurrentHashMap<>();
    private final Map<String, String> openBasketByTill = new ConcurrentHashMap<>();
    private final Map<String, String> lastClosedByTill = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> transactionCounters = new ConcurrentHashMap<>();

    public TillService(CatalogueClient catalogue, StockClient stock, OrderClient orders, PaymentTerminal terminal,
                       BasketPricer pricer, TillProperties properties) {
        this.catalogue = catalogue;
        this.stock = stock;
        this.orders = orders;
        this.terminal = terminal;
        this.pricer = pricer;
        this.properties = properties;
    }

    public TillSession session(String requestedTillId, Map<String, String> services) {
        String tillId = tillId(requestedTillId);
        Basket basket = openBasket(tillId);
        boolean connected = services.values().stream().allMatch("UP"::equals);
        return new TillSession(properties.storeNumber(), properties.storeName(), tillId,
                new Operator(properties.operatorId(), properties.operatorName()), "GBP",
                basket.getTransactionNumber(), basket.getBasketId(), connected, services);
    }

    public List<CatalogueItem> quickKeys() {
        return catalogue.quickKeys();
    }

    public CatalogueItem product(String barcode) {
        return catalogue.findByBarcode(barcode).orElseThrow(() ->
                new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_BARCODE", "No product for barcode " + barcode));
    }

    public Basket basket(String basketId) {
        Basket basket = baskets.get(basketId);
        if (basket == null) {
            throw new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_BASKET", "No basket " + basketId);
        }
        return basket;
    }

    public synchronized Basket addLine(String basketId, String barcode, int quantity) {
        Basket basket = openForChange(basketId);
        CatalogueItem item = product(barcode);
        if (!stock.isInStock(item.skuCode())) {
            throw new TillException(HttpStatus.CONFLICT, "OUT_OF_STOCK", item.name() + " is out of stock");
        }
        BasketLine line = basket.getLines().stream().filter(l -> l.getBarcode().equals(barcode)).findFirst()
                .orElse(null);
        if (line == null) {
            line = new BasketLine(UUID.randomUUID().toString(), item.barcode(), item.skuCode(), item.name(),
                    0, item.pricePence(), 0);
            line.setVatCode(VatRate.of(item.vatCode()).name());
            basket.getLines().add(line);
        }
        line.setQuantity(line.getQuantity() + quantity);
        basket.setLastLineId(line.getLineId());
        return pricer.price(basket);
    }

    public synchronized Basket updateLine(String basketId, String lineId, int quantity) {
        Basket basket = openForChange(basketId);
        BasketLine line = line(basket, lineId);
        line.setQuantity(quantity);
        basket.setLastLineId(lineId);
        return pricer.price(basket);
    }

    public synchronized Basket voidLine(String basketId, String lineId) {
        Basket basket = openForChange(basketId);
        BasketLine line = line(basket, lineId);
        basket.getLines().remove(line);
        List<BasketLine> lines = basket.getLines();
        basket.setLastLineId(lines.isEmpty() ? null : lines.get(lines.size() - 1).getLineId());
        return pricer.price(basket);
    }

    /** Voids the whole sale and returns the next open basket for the same till. */
    public synchronized Basket voidSale(String basketId) {
        Basket basket = openForChange(basketId);
        basket.setStatus(Basket.Status.VOIDED);
        openBasketByTill.remove(basket.getTillId(), basketId);
        retireClosed(basket);
        log.info("Till {} voided transaction {}", basket.getTillId(), basket.getTransactionNumber());
        return openBasket(basket.getTillId());
    }

    public synchronized TenderResult tender(TenderRequest request) {
        Basket basket = openForChange(request.basketId());
        String tenderType = request.tenderType().toUpperCase();
        if (!TENDER_TYPES.contains(tenderType)) {
            throw new TillException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TENDER", "Unsupported tender " + tenderType);
        }
        if (basket.getLines().isEmpty()) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_BASKET", "Basket has no items");
        }
        pricer.price(basket);
        int total = basket.getTotalPence();
        if (request.expectedTotalPence() != null && request.expectedTotalPence() != total) {
            throw new TillException(HttpStatus.CONFLICT, "TOTAL_CHANGED", "Basket total changed; review the basket before taking payment");
        }
        int tendered = total;
        String authCode = null;
        if ("CASH".equals(tenderType)) {
            tendered = request.amountTenderedPence() == null ? total : request.amountTenderedPence();
            if (tendered < total) {
                throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_TENDER",
                        "Cash tendered is less than the total to pay");
            }
        } else {
            PaymentTerminal.Authorisation auth = terminal.authorise(basket.getBasketId(), tenderType, total,
                    request.terminalOutcome());
            if (!auth.approved()) {
                log.info("Till {} card declined for transaction {}", basket.getTillId(), basket.getTransactionNumber());
                return new TenderResult("DECLINED", tenderType, basket, null, 0, 0, null, auth.declineReason(),
                        null, basket.getBasketId());
            }
            authCode = auth.authCode();
        }
        String orderNumber = orders.placeSale(basket, properties.storeNumber(), tenderType);
        basket.setOrderNumber(orderNumber);
        basket.setStatus(Basket.Status.COMPLETED);
        openBasketByTill.remove(basket.getTillId(), basket.getBasketId());
        retireClosed(basket);
        Basket next = openBasket(basket.getTillId());
        int change = tendered - total;
        Receipt receipt = new Receipt("R-" + basket.getTransactionNumber(), properties.storeNumber(),
                properties.storeName(), basket.getTillId(), properties.operatorName(), basket.getTransactionNumber(),
                orderNumber, List.copyOf(basket.getLines()), basket.getSubtotalPence(), basket.getSavingsPence(),
                total, tenderType, tendered, change, authCode, Instant.now(), basket.getVatSummary());
        log.info("Till {} completed transaction {} as order {} ({}p {})", basket.getTillId(),
                basket.getTransactionNumber(), orderNumber, total, tenderType);
        return new TenderResult("APPROVED", tenderType, basket, orderNumber, tendered, change, authCode, null,
                receipt, next.getBasketId());
    }

    private String tillId(String requested) {
        return requested == null || requested.isBlank() ? properties.defaultTillId() : requested;
    }

    private Basket openBasket(String tillId) {
        if (!openBasketByTill.containsKey(tillId) && openBasketByTill.size() >= MAX_TILLS) {
            throw new TillException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_TILLS", "Too many active tills");
        }
        String id = openBasketByTill.computeIfAbsent(tillId, t -> {
            Basket basket = new Basket();
            basket.setBasketId(UUID.randomUUID().toString());
            basket.setTillId(t);
            int n = transactionCounters.computeIfAbsent(t, k -> new AtomicInteger()).incrementAndGet();
            basket.setTransactionNumber(String.format("%s-%s-%04d", properties.storeNumber(), t, n));
            baskets.put(basket.getBasketId(), pricer.price(basket));
            return basket.getBasketId();
        });
        return baskets.get(id);
    }

    /** Keeps only the most recently closed basket per till readable, so memory stays bounded per till. */
    private void retireClosed(Basket basket) {
        String previous = lastClosedByTill.put(basket.getTillId(), basket.getBasketId());
        if (previous != null) {
            baskets.remove(previous);
        }
    }

    private Basket openForChange(String basketId) {
        Basket basket = basket(basketId);
        if (basket.getStatus() != Basket.Status.OPEN) {
            throw new TillException(HttpStatus.CONFLICT, "BASKET_CLOSED", "Basket is " + basket.getStatus());
        }
        return basket;
    }

    private static BasketLine line(Basket basket, String lineId) {
        return basket.getLines().stream().filter(l -> l.getLineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_LINE", "No line " + lineId));
    }
}
