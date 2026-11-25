package com.ibatulanand.tillservice.service;

import com.ibatulanand.tillservice.barcode.EmbeddedPriceBarcode;
import com.ibatulanand.tillservice.client.CatalogueClient;
import com.ibatulanand.tillservice.client.OrderClient;
import com.ibatulanand.tillservice.client.StockClient;
import com.ibatulanand.tillservice.config.TillProperties;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.loyalty.MoreCardDirectory;
import com.ibatulanand.tillservice.loyalty.MoreCardMember;
import com.ibatulanand.tillservice.loyalty.MoreCardProperties;
import com.ibatulanand.tillservice.domain.TenderLine;
import com.ibatulanand.tillservice.payment.PaymentTerminal;
import com.ibatulanand.tillservice.pricing.BasketPricer;
import com.ibatulanand.tillservice.web.TillDtos.MoreCardReceipt;
import com.ibatulanand.tillservice.web.TillDtos.CashQuote;
import com.ibatulanand.tillservice.web.TillDtos.OfflineSaleLine;
import com.ibatulanand.tillservice.web.TillDtos.OfflineSaleRequest;
import com.ibatulanand.tillservice.web.TillDtos.OfflineSaleResult;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private static final int MAX_CARD_INPUT = 64;

    private final Optional<MoreCardDirectory> moreCards;
    private final MoreCardProperties moreCardProperties;

    static final int MAX_TILLS = 10_000;
    static final int MAX_SETTLED_ATTEMPTS = 10_000;
    private static final java.util.regex.Pattern IDEMPOTENCY_KEY = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final Map<String, Basket> baskets = new ConcurrentHashMap<>();
    private final Map<String, String> openBasketByTill = new ConcurrentHashMap<>();
    private final Map<String, String> lastClosedByTill = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> transactionCounters = new ConcurrentHashMap<>();
    /** Basket id -> key of the card attempt waiting on the PIN pad. */
    private final Map<String, String> tenderInFlight = new ConcurrentHashMap<>();
    /** Basket id -> key of a card attempt that timed out; only a retry with that key may tender the basket. */
    private final Map<String, String> unresolvedAttemptByBasket = new ConcurrentHashMap<>();
    /** Final (approved/declined) results by idempotency key, replayed for repeated requests. */
    private final Map<String, TenderResult> settledAttempts = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TenderResult> eldest) {
                    return size() > MAX_SETTLED_ATTEMPTS;
                }
            });
    private final Map<String, CompletableFuture<Void>> attemptsInProgress = new ConcurrentHashMap<>();

    public TillService(CatalogueClient catalogue, StockClient stock, OrderClient orders, PaymentTerminal terminal,
                       BasketPricer pricer, TillProperties properties, Optional<MoreCardDirectory> moreCards,
                       MoreCardProperties moreCardProperties) {
        this.catalogue = catalogue;
        this.stock = stock;
        this.orders = orders;
        this.terminal = terminal;
        this.pricer = pricer;
        this.properties = properties;
        this.moreCards = moreCards;
        this.moreCardProperties = moreCardProperties;
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
        return addLine(basketId, barcode, quantity, null);
    }

    /**
     * Adds a scanned or keyed item. A price-embedded label (prefix-2 EAN-13) adds one line at the label price;
     * an item sold by weight needs {@code weightGrams} and is priced at its per-kg price.
     */
    public synchronized Basket addLine(String basketId, String barcode, int quantity, Integer weightGrams) {
        Basket basket = openForLineChange(basketId);
        // A catalogued barcode wins over the price-label reading of the same digits.
        Optional<CatalogueItem> exact = EmbeddedPriceBarcode.isCandidate(barcode)
                ? catalogue.findByBarcode(barcode) : Optional.empty();
        Optional<EmbeddedPriceBarcode> label = exact.isPresent() ? Optional.empty() : EmbeddedPriceBarcode.parse(barcode);
        CatalogueItem item = exact.orElseGet(() -> product(label.map(EmbeddedPriceBarcode::plu).orElse(barcode)));
        if (label.isEmpty() && item.soldByWeight() && weightGrams == null) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "WEIGHT_REQUIRED",
                    "Enter the weight in kg for " + item.name());
        }
        if (label.isPresent() && !item.soldByWeight()) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_SOLD_BY_WEIGHT",
                    "Barcode " + barcode + " is a price label but " + item.name() + " is not sold by weight");
        }
        if (label.isPresent() && label.get().pricePence() < 1) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LABEL_PRICE",
                    "Barcode " + barcode + " has no price. Reprint the label or weigh the item.");
        }
        if (weightGrams != null && (label.isPresent() || !item.soldByWeight())) {
            throw new TillException(HttpStatus.BAD_REQUEST, "NOT_SOLD_BY_WEIGHT", item.name() + " is not sold by weight");
        }
        if ((label.isPresent() || weightGrams != null) && quantity != 1) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "QUANTITY_NOT_ALLOWED",
                    "Weighed and labelled items are sold one per line; scan or weigh another");
        }
        if (weightGrams != null && BasketLine.weighedPence(weightGrams, item.pricePence()) < 1) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "WEIGHT_TOO_LOW",
                    "Weight is too low to charge for " + item.name() + "; weigh again");
        }
        if (!stock.isInStock(item.skuCode())) {
            throw new TillException(HttpStatus.CONFLICT, "OUT_OF_STOCK", item.name() + " is out of stock");
        }
        BasketLine line;
        if (label.isPresent()) {
            line = new BasketLine(UUID.randomUUID().toString(), barcode, item.skuCode(), item.name(), 1,
                    label.get().pricePence(), 0, BasketLine.PricedBy.LABEL, null);
            line.setVatCode(VatRate.of(item.vatCode()).name());
            applyAgeRestriction(line, item);
            basket.getLines().add(line);
        } else if (weightGrams != null) {
            line = new BasketLine(UUID.randomUUID().toString(), item.barcode(), item.skuCode(), item.name(), 1,
                    item.pricePence(), 0, BasketLine.PricedBy.WEIGHT, weightGrams);
            line.setVatCode(VatRate.of(item.vatCode()).name());
            applyAgeRestriction(line, item);
            basket.getLines().add(line);
        } else {
            line = basket.getLines().stream()
                    .filter(l -> l.getPricedBy() == BasketLine.PricedBy.EACH && l.getBarcode().equals(barcode))
                    .findFirst().orElse(null);
            if (line == null) {
                line = new BasketLine(UUID.randomUUID().toString(), item.barcode(), item.skuCode(), item.name(),
                        0, item.pricePence(), 0);
                line.setVatCode(VatRate.of(item.vatCode()).name());
            applyAgeRestriction(line, item);
                basket.getLines().add(line);
            }
            line.setQuantity(line.getQuantity() + quantity);
        }
        basket.setLastLineId(line.getLineId());
        return pricer.price(basket);
    }

    public synchronized Basket updateLine(String basketId, String lineId, int quantity) {
        Basket basket = openForLineChange(basketId);
        BasketLine line = line(basket, lineId);
        if (line.getPricedBy() != BasketLine.PricedBy.EACH && quantity != line.getQuantity()) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "QUANTITY_NOT_ALLOWED",
                    "Weighed and labelled items are sold one per line; scan or weigh another");
        }
        line.setQuantity(quantity);
        basket.setLastLineId(lineId);
        return pricer.price(basket);
    }

    public synchronized Basket voidLine(String basketId, String lineId) {
        Basket basket = openForLineChange(basketId);
        BasketLine line = line(basket, lineId);
        basket.getLines().remove(line);
        List<BasketLine> lines = basket.getLines();
        basket.setLastLineId(lines.isEmpty() ? null : lines.get(lines.size() - 1).getLineId());
        return pricer.price(basket);
    }

    /** Attaches a More Card holder to the open transaction so member prices apply and points are earned. */
    public synchronized Basket attachMember(String basketId, String cardNumber) {
        if (cardNumber.length() > MAX_CARD_INPUT) {
            throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_CARD", "More Card number is too long");
        }
        Basket basket = openForChange(basketId);
        String card = cardNumber.replaceAll("\\s", "");
        MoreCardDirectory directory = moreCards.orElseThrow(() -> new TillException(HttpStatus.SERVICE_UNAVAILABLE,
                "MORE_CARD_UNAVAILABLE", "More Card is not available on this till. Continuing as non-member."));
        MoreCardMember member = directory.find(card).orElseThrow(() -> new TillException(HttpStatus.NOT_FOUND,
                "UNKNOWN_CARD", basket.getMember() == null
                        ? "More Card not recognised. Continuing as non-member."
                        : "More Card not recognised. Keeping " + basket.getMember().name() + "'s card."));
        basket.setMember(member);
        basket.setMemberCardNumber(card);
        log.info("Till {} attached More Card ending {} to transaction {}", basket.getTillId(), member.cardLast4(),
                basket.getTransactionNumber());
        return pricer.price(basket);
    }

    /**
     * Challenge 25 decision for an age-restricted line: APPROVED records the till's signed-in operator as the approver;
     * REFUSED removes the line from the basket.
     */
    private static void applyAgeRestriction(BasketLine line, CatalogueItem item) {
        if (item.ageRestricted()) {
            line.setMinimumAge(item.minimumAge());
            line.setAgeCheck(BasketLine.AgeCheck.PENDING);
        }
    }

    public synchronized Basket ageCheck(String basketId, String lineId, String decision) {
        Basket basket = openForChange(basketId);
        BasketLine line = line(basket, lineId);
        if (line.getAgeCheck() == null) {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_AGE_RESTRICTED",
                    line.getName() + " is not age restricted");
        }
        switch (decision.toUpperCase()) {
            case "APPROVED" -> {
                line.setAgeCheck(BasketLine.AgeCheck.APPROVED);
                line.setAgeApprovedBy(properties.operatorId());
                basket.setLastLineId(lineId);
                log.info("Till {} age check approved by {} for {} on transaction {}", basket.getTillId(),
                        line.getAgeApprovedBy(), line.getSkuCode(), basket.getTransactionNumber());
                return pricer.price(basket);
            }
            case "REFUSED" -> {
                log.info("Till {} age check refused for {} on transaction {}", basket.getTillId(), line.getSkuCode(),
                        basket.getTransactionNumber());
                return voidLine(basketId, lineId);
            }
            default -> throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_AGE_DECISION",
                    "Age check decision must be APPROVED or REFUSED");
        }
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

    /**
     * Takes payment for a basket. Each attempt carries an idempotency key: repeating a request with the same key
     * (a retry after a timeout, a double tap) returns the first attempt's result and never charges or orders twice.
     * A card attempt that timed out stays unresolved until it is retried with the same key.
     */
    public TenderResult tender(TenderRequest request) {
        String key = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? UUID.randomUUID().toString() : request.idempotencyKey();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency key must be 1-64 letters, digits, '.', '_', ':' or '-'");
        }
        // Duplicates of one attempt run one at a time; attempts with other keys never wait on each other.
        while (true) {
            TenderResult previous = settledAttempt(key, request.basketId());
            if (previous != null) {
                return previous;
            }
            CompletableFuture<Void> mine = new CompletableFuture<>();
            CompletableFuture<Void> running = attemptsInProgress.putIfAbsent(key, mine);
            if (running != null) {
                running.join();
                continue;
            }
            try {
                previous = settledAttempt(key, request.basketId());
                if (previous != null) {
                    return previous;
                }
                TenderResult result = attemptTender(request, key);
                if (!"TIMEOUT".equals(result.status())) {
                    settledAttempts.put(key, result);
                }
                return result;
            } finally {
                attemptsInProgress.remove(key, mine);
                mine.complete(null);
            }
        }
    }

    private TenderResult settledAttempt(String key, String basketId) {
        TenderResult previous = settledAttempts.get(key);
        if (previous == null) {
            return null;
        }
        if (!previous.basket().getBasketId().equals(basketId)) {
            throw new TillException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key already used for another basket");
        }
        log.info("Till {} replayed tender attempt {} ({})", previous.basket().getTillId(), key, previous.status());
        return previous;
    }

    private TenderResult attemptTender(TenderRequest request, String key) {
        String tenderType = request.tenderType().toUpperCase();
        Basket basket;
        int balance;
        synchronized (this) {
            basket = basket(request.basketId());
            String unresolved = unresolvedAttemptByBasket.get(basket.getBasketId());
            if (unresolved != null && !unresolved.equals(key)) {
                throw new TillException(HttpStatus.CONFLICT, "PAYMENT_UNRESOLVED",
                        "The last card payment timed out; retry it before taking another payment");
            }
            openForChange(basket.getBasketId(), unresolved != null);
            if (unresolved != null && "CASH".equals(tenderType)) {
                throw new TillException(HttpStatus.CONFLICT, "PAYMENT_UNRESOLVED",
                        "The last card payment timed out; retry it on the card terminal");
            }
            if (!TENDER_TYPES.contains(tenderType)) {
                throw new TillException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TENDER", "Unsupported tender " + tenderType);
            }
            if (basket.getLines().isEmpty()) {
                throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_BASKET", "Basket has no items");
            }
            if (basket.getPendingAgeChecks() > 0) {
                throw new TillException(HttpStatus.CONFLICT, "AGE_CHECK_REQUIRED",
                        "Approve or refuse age-restricted items before taking payment");
            }
            pricer.price(basket);
            int total = basket.getTotalPence();
            if (request.expectedTotalPence() != null && request.expectedTotalPence() != total) {
                throw new TillException(HttpStatus.CONFLICT, "TOTAL_CHANGED", "Basket total changed; review the basket before taking payment");
            }
            balance = basket.getBalanceDuePence();
            if (request.expectedBalanceDuePence() != null && request.expectedBalanceDuePence() != balance) {
                throw new TillException(HttpStatus.CONFLICT, "BALANCE_CHANGED",
                        "A payment was already taken on this basket; check the amount left to pay");
            }
            if ("CASH".equals(tenderType)) {
                int tendered = request.amountTenderedPence() == null ? balance : request.amountTenderedPence();
                if (tendered < balance) {
                    if (!Boolean.TRUE.equals(request.partialPayment()) || tendered <= 0) {
                        throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_TENDER",
                                "Cash tendered is less than the total to pay");
                    }
                    basket.getTenders().add(new TenderLine("CASH", tendered, tendered, 0, null));
                    log.info("Till {} took {}p part cash on transaction {}, {}p left to pay", basket.getTillId(), tendered,
                            basket.getTransactionNumber(), basket.getBalanceDuePence());
                    return new TenderResult("PARTIAL", tenderType, basket, null, tendered, 0, null, null, null,
                            basket.getBasketId(), basket.getBalanceDuePence(), key);
                }
                return complete(basket, tenderType, new TenderLine("CASH", balance, tendered, tendered - balance, null), key);
            }
            tenderInFlight.put(basket.getBasketId(), key);
        }
        // The PIN pad can take seconds; other tills keep trading while this one waits.
        PaymentTerminal.Authorisation auth;
        try {
            auth = terminal.authorise(key, basket.getBasketId(), tenderType, balance, request.terminalOutcome());
        } catch (RuntimeException e) {
            synchronized (this) {
                tenderInFlight.remove(basket.getBasketId());
            }
            throw e;
        }
        // Release the in-flight lock in the same critical section that settles the basket, so no other tender
        // can slip in between a card approval and the sale being recorded.
        synchronized (this) {
            tenderInFlight.remove(basket.getBasketId());
            switch (auth.outcome()) {
                case APPROVED -> {
                    try {
                        TenderResult completed = complete(basket, tenderType, new TenderLine(tenderType, balance, balance, 0, auth.authCode()), key);
                        unresolvedAttemptByBasket.remove(basket.getBasketId());
                        return completed;
                    } catch (RuntimeException e) {
                        // The card is charged but the order was not recorded: keep the basket locked to this attempt.
                        unresolvedAttemptByBasket.put(basket.getBasketId(), key);
                        throw e;
                    }
                }
                case DECLINED -> {
                    unresolvedAttemptByBasket.remove(basket.getBasketId());
                    log.info("Till {} card declined for transaction {}", basket.getTillId(), basket.getTransactionNumber());
                    return new TenderResult("DECLINED", tenderType, basket, null, 0, 0, null, auth.declineReason(),
                            null, basket.getBasketId(), balance, key);
                }
                default -> {
                    unresolvedAttemptByBasket.put(basket.getBasketId(), key);
                    log.info("Till {} terminal timed out for transaction {} (attempt {})", basket.getTillId(),
                            basket.getTransactionNumber(), key);
                    return new TenderResult("TIMEOUT", tenderType, basket, null, 0, 0, null, auth.declineReason(),
                            null, basket.getBasketId(), balance, key);
                }
            }
        }
    }

    private TenderResult complete(Basket basket, String tenderType, TenderLine tenderLine, String key) {
        int total = basket.getTotalPence();
        basket.getTenders().add(tenderLine);
        List<TenderLine> tenders = List.copyOf(basket.getTenders());
        String orderTenderType = tenders.stream().map(TenderLine::tenderType).distinct().count() > 1 ? "SPLIT" : tenderType;
        String orderNumber;
        try {
            orderNumber = orders.placeSale(basket, properties.storeNumber(), orderTenderType);
        } catch (RuntimeException e) {
            basket.getTenders().remove(basket.getTenders().size() - 1);
            throw e;
        }
        MoreCardReceipt moreCard = awardPoints(basket);
        basket.setOrderNumber(orderNumber);
        basket.setStatus(Basket.Status.COMPLETED);
        openBasketByTill.remove(basket.getTillId(), basket.getBasketId());
        retireClosed(basket);
        Basket next = openBasket(basket.getTillId());
        int tenderedTotal = tenders.stream().mapToInt(TenderLine::tenderedPence).sum();
        int changeTotal = tenders.stream().mapToInt(TenderLine::changeDuePence).sum();
        Receipt receipt = new Receipt("R-" + basket.getTransactionNumber(), properties.storeNumber(),
                properties.storeName(), basket.getTillId(), properties.operatorName(), basket.getTransactionNumber(),
                orderNumber, List.copyOf(basket.getLines()), basket.getSubtotalPence(), basket.getSavingsPence(),
                total, orderTenderType, tenderedTotal, changeTotal, tenderLine.authCode(), Instant.now(),
                basket.getVatSummary(), moreCard, tenders);
        log.info("Till {} completed transaction {} as order {} ({}p {})", basket.getTillId(),
                basket.getTransactionNumber(), orderNumber, total, orderTenderType);
        return new TenderResult("APPROVED", tenderType, basket, orderNumber, tenderLine.tenderedPence(),
                tenderLine.changeDuePence(), tenderLine.authCode(), null, receipt, next.getBasketId(), 0, key);
    }

    /** Previews a cash tender without taking it, so the till never computes change itself. */
    public synchronized CashQuote cashQuote(String basketId, int amountTenderedPence) {
        if (amountTenderedPence < 0) {
            throw new TillException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Cash amount cannot be negative");
        }
        Basket basket = basket(basketId);
        int balance = basket.getBalanceDuePence();
        return new CashQuote(basket.getTotalPence(), basket.getPaidPence(), balance, amountTenderedPence,
                Math.max(0, amountTenderedPence - balance), Math.max(0, balance - amountTenderedPence));
    }

    private MoreCardReceipt awardPoints(Basket basket) {
        if (basket.getMemberCardNumber() == null) {
            return null;
        }
        int points = moreCardProperties.pointsFor(basket.getTotalPence());
        MoreCardMember member = moreCards.orElseThrow().award(basket.getMemberCardNumber(), points);
        basket.setMember(member);
        return new MoreCardReceipt(member.cardLast4(), member.name(), points, member.pointsBalance());
    }

    /**
     * Persists a cash sale the till completed offline (store-and-forward). The sale already happened, so stock is
     * not re-checked and the catalogue price is used when known, else the till's cached price. order-service keys
     * the order on {@code offline-<saleId>}, so replays return the same order instead of a duplicate.
     */
    public OfflineSaleResult syncOfflineSale(OfflineSaleRequest request) {
        if (!"CASH".equalsIgnoreCase(request.tenderType())) {
            throw new TillException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TENDER", "Only cash is taken offline");
        }
        Basket basket = new Basket();
        basket.setBasketId(request.saleId());
        basket.setTillId(tillId(request.tillId()));
        basket.setTransactionNumber(request.transactionNumber() == null || request.transactionNumber().isBlank()
                ? "OFFLINE-" + request.saleId() : request.transactionNumber());
        for (OfflineSaleLine sold : request.lines()) {
            CatalogueItem item = catalogue.findByBarcode(sold.barcode()).orElse(null);
            if (item == null && (sold.skuCode() == null || sold.unitPricePence() == null)) {
                throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_BARCODE",
                        "No product for barcode " + sold.barcode());
            }
            // The customer paid the price the till charged offline, so that price is recorded, not today's.
            int unitPrice = sold.unitPricePence() != null ? sold.unitPricePence() : item.pricePence();
            basket.getLines().add(new BasketLine(UUID.randomUUID().toString(), sold.barcode(),
                    item != null ? item.skuCode() : sold.skuCode(), item != null ? item.name() : sold.name(),
                    sold.quantity(), unitPrice, 0));
        }
        pricer.price(basket);
        int tendered = request.amountTenderedPence() == null ? basket.getTotalPence() : request.amountTenderedPence();
        String orderNumber = orders.placeSale(basket, properties.storeNumber(), "CASH", "offline-" + request.saleId());
        log.info("Till {} synced offline sale {} as order {} ({}p)", loggable(basket.getTillId()),
                loggable(request.saleId()), orderNumber, basket.getTotalPence());
        return new OfflineSaleResult(request.saleId(), orderNumber, basket.getTransactionNumber(),
                basket.getTotalPence(), tendered);
    }

    private static String loggable(String value) {
        return value.replaceAll("[\\r\\n\\t]", "_");
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

    private Basket openForLineChange(String basketId) {
        Basket basket = openForChange(basketId);
        if (!basket.getTenders().isEmpty()) {
            throw new TillException(HttpStatus.CONFLICT, "TENDER_IN_PROGRESS",
                    "Payment has started; finish the payment or void the sale");
        }
        return basket;
    }

    private Basket openForChange(String basketId) {
        return openForChange(basketId, false);
    }

    private Basket openForChange(String basketId, boolean retryingUnresolved) {
        Basket basket = basket(basketId);
        if (basket.getStatus() != Basket.Status.OPEN) {
            throw new TillException(HttpStatus.CONFLICT, "BASKET_CLOSED", "Basket is " + basket.getStatus());
        }
        if (tenderInFlight.containsKey(basketId)) {
            throw new TillException(HttpStatus.CONFLICT, "TENDER_IN_PROGRESS", "Waiting for the PIN pad");
        }
        if (!retryingUnresolved && unresolvedAttemptByBasket.containsKey(basketId)) {
            throw new TillException(HttpStatus.CONFLICT, "PAYMENT_UNRESOLVED",
                    "The last card payment timed out; retry it before changing the basket");
        }
        return basket;
    }

    private static BasketLine line(Basket basket, String lineId) {
        return basket.getLines().stream().filter(l -> l.getLineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_LINE", "No line " + lineId));
    }
}
