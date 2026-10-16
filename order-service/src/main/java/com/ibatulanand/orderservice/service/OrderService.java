package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.dto.OrderTotals;
import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.dto.ReceiptDeliveryDto;
import com.ibatulanand.orderservice.dto.ServiceFeeDto;
import com.ibatulanand.orderservice.dto.TenderDto;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.FulfillmentType;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.model.OrderLinePromotion;
import com.ibatulanand.orderservice.model.OrderServiceFee;
import com.ibatulanand.orderservice.model.OrderTender;
import com.ibatulanand.orderservice.model.Promotion;
import com.ibatulanand.orderservice.model.ReceiptChannel;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.PricingService.PricedLine;
import com.ibatulanand.orderservice.service.PricingService.PricedLineInput;
import com.ibatulanand.orderservice.service.PricingService.PricingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class OrderService {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String NOTIFICATION_TOPIC = "notificationTopic";

    private final OrderRepository orderRepository;
    private final PricingService pricingService;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${order.inventory-service.base-url:http://inventory-service}")
    private String inventoryServiceUrl;

    public OrderService(OrderRepository orderRepository,
                        PricingService pricingService,
                        WebClient.Builder webClientBuilder,
                        KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.pricingService = pricingService;
        this.webClientBuilder = webClientBuilder;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OrderResponse placeOrder(OrderRequest request) {
        validate(request);
        BigDecimal taxRate = pricingService.taxRateFor(request.getStoreId());
        List<Promotion> promotions = pricingService.resolvePromotions(request.getPromotions());

        List<PricedLineInput> pricingInputs = new ArrayList<>();
        for (OrderLineItemsDto line : request.getLineItems()) {
            ProductResponse product = pricingService.lookupProduct(line.getSkuCode().trim());
            pricingInputs.add(new PricedLineInput(product, line.getQuantity()));
        }
        checkStock(request, pricingInputs);

        BigDecimal servicesAndFees = request.getServices().stream()
                .map(ServiceFeeDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PricingResult pricing = pricingService.price(pricingInputs, promotions, servicesAndFees,
                request.isTaxExempt(), taxRate);

        BigDecimal tendered = request.getTenders().stream()
                .map(TenderDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (tendered.compareTo(pricing.totals().getTotal()) != 0) {
            throw OrderProblemException.validationFailed("Tenders total " + tendered.toPlainString()
                    + " does not equal order total " + pricing.totals().getTotal().toPlainString());
        }

        Order order = buildOrder(request, pricing);
        OrderPlacedEvent event = toEvent(order, request, pricing);
        transactionTemplate.executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            orderRepository.save(order);
        });

        return toResponse(order, pricing);
    }

    private void validate(OrderRequest request) {
        requireText(request.getStoreId(), "storeId");
        requireText(request.getRegisterId(), "registerId");
        requireText(request.getAssociateId(), "associateId");
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            throw OrderProblemException.validationFailed("lineItems must not be empty");
        }
        for (OrderLineItemsDto line : request.getLineItems()) {
            requireText(line.getSkuCode(), "lineItems[].skuCode");
            if (line.getQuantity() == null || line.getQuantity() < 1) {
                throw OrderProblemException.validationFailed("lineItems[].quantity must be at least 1");
            }
            if (line.getFulfillmentType() == null) {
                line.setFulfillmentType(FulfillmentType.TAKE_WITH);
            }
            if (line.getFulfillmentType() == FulfillmentType.TAKE_WITH) {
                line.setFulfillmentStoreId(request.getStoreId());
            }
        }
        if (request.getPromotions() == null) {
            request.setPromotions(List.of());
        }
        if (request.getServices() == null) {
            request.setServices(List.of());
        }
        for (ServiceFeeDto service : request.getServices()) {
            requireText(service.getCode(), "services[].code");
            if (service.getAmount() == null || service.getAmount().signum() < 0) {
                throw OrderProblemException.validationFailed("services[].amount must be zero or more");
            }
        }
        if (request.getTenders() == null) {
            request.setTenders(List.of());
        }
        for (TenderDto tender : request.getTenders()) {
            if (tender.getType() == null) {
                throw OrderProblemException.validationFailed("tenders[].type is required");
            }
            if (tender.getAmount() == null || tender.getAmount().signum() < 0) {
                throw OrderProblemException.validationFailed("tenders[].amount must be zero or more");
            }
        }
        if (request.isTaxExempt()) {
            requireText(request.getTaxExemptCertificate(), "taxExemptCertificate (required when taxExempt)");
            if (request.getTaxExemptCertificate().length() > 64) {
                throw OrderProblemException.validationFailed("taxExemptCertificate must be at most 64 characters");
            }
        }
        ReceiptDeliveryDto receipt = request.getReceiptDelivery();
        if (receipt != null) {
            if (receipt.getChannel() == null) {
                throw OrderProblemException.validationFailed("receiptDelivery.channel is required");
            }
            if ((receipt.getChannel() == ReceiptChannel.EMAIL || receipt.getChannel() == ReceiptChannel.SMS)
                    && isBlank(receipt.getDestination())) {
                throw OrderProblemException.validationFailed(
                        "receiptDelivery.destination is required for " + receipt.getChannel());
            }
        }
    }

    /**
     * POS-2 frozen contract, one call per SKU at the selling store. Unknown SKU or no inventory
     * row is a 409, so an empty response can never let an order through.
     */
    /** Quantities are aggregated per (SKU, source store) across all lines, then checked once each. */
    private void checkStock(OrderRequest request, List<PricedLineInput> pricingInputs) {
        String sellingStore = request.getStoreId();
        Map<String, InventoryResponse> inventoryBySku = new LinkedHashMap<>();
        Map<String, Map<String, Long>> quantityBySkuAndSource = new LinkedHashMap<>();
        for (int i = 0; i < pricingInputs.size(); i++) {
            OrderLineItemsDto line = request.getLineItems().get(i);
            String sku = pricingInputs.get(i).product().getSkuCode();
            InventoryResponse inventory = inventoryBySku.computeIfAbsent(sku, s -> fetchInventory(s, sellingStore));

            String source = sellingStore;
            if (line.getFulfillmentType() == FulfillmentType.SHIP_FROM_STORE) {
                if (!inventory.isShipFromStoreEligible()) {
                    throw OrderProblemException.outOfStock(sku + " is not eligible for ship-from-store");
                }
                if (isBlank(line.getFulfillmentStoreId())) {
                    line.setFulfillmentStoreId(sellingStore);
                }
                source = line.getFulfillmentStoreId();
            }
            quantityBySkuAndSource.computeIfAbsent(sku, s -> new LinkedHashMap<>())
                    .merge(source, line.getQuantity().longValue(), Long::sum);
        }
        quantityBySkuAndSource.forEach((sku, bySource) -> bySource.forEach((source, quantity) -> {
            int onHand = onHandAt(inventoryBySku.get(sku), sellingStore, source);
            if (onHand < quantity) {
                throw OrderProblemException.outOfStock(sku + " has " + onHand + " on hand at store " + source
                        + ", " + quantity + " requested");
            }
        }));
    }

    private static int onHandAt(InventoryResponse inventory, String sellingStore, String source) {
        if (source.equals(sellingStore)) {
            return Objects.requireNonNullElse(inventory.getOnHand(), 0);
        }
        if (inventory.getNearbyStores() == null) {
            return 0;
        }
        return inventory.getNearbyStores().stream()
                .filter(s -> source.equals(s.getStoreId()) && s.getOnHand() != null)
                .mapToInt(s -> s.getOnHand())
                .findFirst()
                .orElse(0);
    }

    private InventoryResponse fetchInventory(String skuCode, String storeId) {
        InventoryResponse inventory = webClientBuilder.build().get()
                .uri(inventoryServiceUrl + "/api/inventory",
                        b -> b.queryParam("skuCode", skuCode).queryParam("storeId", storeId).build())
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.error(
                        OrderProblemException.outOfStock("No inventory for " + skuCode + " at store " + storeId)))
                .bodyToMono(InventoryResponse.class)
                .block();
        if (inventory == null) {
            throw OrderProblemException.outOfStock("No inventory for " + skuCode + " at store " + storeId);
        }
        return inventory;
    }

    private Order buildOrder(OrderRequest request, PricingResult pricing) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStoreId(request.getStoreId());
        order.setRegisterId(request.getRegisterId());
        order.setAssociateId(request.getAssociateId());
        order.setStatus(STATUS_COMPLETED);
        order.setTaxExempt(request.isTaxExempt());
        order.setTaxExemptCertificate(request.isTaxExempt() ? request.getTaxExemptCertificate() : null);
        order.setPlacedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        OrderTotals totals = pricing.totals();
        order.setMerchandiseTotal(totals.getMerchandiseTotal());
        order.setDiscountTotal(totals.getDiscountTotal());
        order.setServicesAndFees(totals.getServicesAndFees());
        order.setTaxableSubtotal(totals.getTaxableSubtotal());
        order.setTaxRate(totals.getTaxRate());
        order.setSalesTax(totals.getSalesTax());
        order.setTotal(totals.getTotal());

        if (request.getReceiptDelivery() != null) {
            order.setReceiptChannel(request.getReceiptDelivery().getChannel());
            order.setReceiptDestination(request.getReceiptDelivery().getDestination());
        }

        for (int i = 0; i < pricing.lines().size(); i++) {
            PricedLine priced = pricing.lines().get(i);
            OrderLineItemsDto requested = request.getLineItems().get(i);
            ProductResponse product = priced.product();
            OrderLineItems line = new OrderLineItems();
            line.setSkuCode(product.getSkuCode());
            line.setQuantity(priced.quantity());
            line.setStyleId(product.getStyleId());
            line.setDescription(product.getDescription());
            line.setColorName(product.getColorName());
            line.setSize(product.getSize());
            line.setListPrice(priced.listPrice());
            line.setUnitPrice(priced.unitPrice());
            line.setPrice(priced.unitPrice());
            line.setMarkdownAmount(priced.markdownAmount());
            line.setClearancePercent(product.getClearancePercent());
            line.setFinalSale(product.isFinalSale());
            line.setExtendedPrice(priced.extendedPrice());
            line.setFulfillmentType(requested.getFulfillmentType());
            line.setFulfillmentStoreId(requested.getFulfillmentStoreId());
            priced.promotionAllocations().forEach((code, amount) ->
                    line.getPromotions().add(new OrderLinePromotion(code, amount)));
            order.getOrderLineItemsList().add(line);
        }
        request.getServices().forEach(s ->
                order.getServices().add(new OrderServiceFee(s.getCode(), s.getDescription(), s.getAmount())));
        request.getTenders().forEach(t ->
                order.getTenders().add(new OrderTender(t.getType(), t.getLabel(), t.getAmount())));
        return order;
    }

    private OrderPlacedEvent toEvent(Order order, OrderRequest request, PricingResult pricing) {
        OrderTotals totals = pricing.totals();
        return OrderPlacedEvent.builder()
                .orderNumber(order.getOrderNumber())
                .storeId(order.getStoreId())
                .registerId(order.getRegisterId())
                .associateId(order.getAssociateId())
                .placedAt(order.getPlacedAt().toString())
                .lineItems(order.getOrderLineItemsList().stream()
                        .map(line -> OrderPlacedEvent.LineItem.builder()
                                .skuCode(line.getSkuCode())
                                .description(line.getDescription())
                                .colorName(line.getColorName())
                                .size(line.getSize())
                                .quantity(line.getQuantity())
                                .unitPrice(line.getUnitPrice())
                                .extendedPrice(line.getExtendedPrice())
                                .finalSale(line.isFinalSale())
                                .fulfillmentType(line.getFulfillmentType().name())
                                .fulfillmentStoreId(line.getFulfillmentStoreId())
                                .build())
                        .toList())
                .promotions(List.copyOf(pricing.promotionTotals().keySet()))
                .services(request.getServices().stream()
                        .map(s -> OrderPlacedEvent.Service.builder()
                                .code(s.getCode())
                                .description(s.getDescription())
                                .amount(s.getAmount())
                                .build())
                        .toList())
                .tenders(request.getTenders().stream()
                        .map(t -> OrderPlacedEvent.Tender.builder()
                                .type(t.getType().name())
                                .amount(t.getAmount())
                                .build())
                        .toList())
                .totals(OrderPlacedEvent.Totals.builder()
                        .merchandiseTotal(totals.getMerchandiseTotal())
                        .servicesAndFees(totals.getServicesAndFees())
                        .discountTotal(totals.getDiscountTotal())
                        .taxableSubtotal(totals.getTaxableSubtotal())
                        .taxRate(totals.getTaxRate())
                        .salesTax(totals.getSalesTax())
                        .total(totals.getTotal())
                        .savedToday(totals.getSavedToday())
                        .taxExempt(totals.isTaxExempt())
                        .build())
                .taxExempt(order.isTaxExempt())
                .receiptDelivery(request.getReceiptDelivery() == null ? null
                        : OrderPlacedEvent.ReceiptDelivery.builder()
                        .channel(request.getReceiptDelivery().getChannel().name())
                        .destination(request.getReceiptDelivery().getDestination())
                        .build())
                .build();
    }

    /** Runs after commit. {@code send} is already async; no extra pool hop, so the TCCL stays the app's. */
    private void publish(OrderPlacedEvent event) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, event.getOrderNumber(), event)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            log.error("Failed to publish OrderPlacedEvent for {}", event.getOrderNumber(), error);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Failed to publish OrderPlacedEvent for {}", event.getOrderNumber(), e);
        }
    }

    private OrderResponse toResponse(Order order, PricingResult pricing) {
        List<OrderResponse.Line> lines = new ArrayList<>();
        for (int i = 0; i < pricing.lines().size(); i++) {
            PricedLine priced = pricing.lines().get(i);
            OrderLineItems line = order.getOrderLineItemsList().get(i);
            lines.add(OrderResponse.Line.builder()
                    .skuCode(line.getSkuCode())
                    .description(line.getDescription())
                    .quantity(line.getQuantity())
                    .listPrice(line.getListPrice())
                    .unitPrice(line.getUnitPrice())
                    .markdownAmount(line.getMarkdownAmount())
                    .extendedPrice(line.getExtendedPrice())
                    .finalSale(line.isFinalSale())
                    .promotionAmount(priced.promotionAmount())
                    .promotions(toApplied(priced.promotionAllocations()))
                    .fulfillmentType(line.getFulfillmentType().name())
                    .fulfillmentStoreId(line.getFulfillmentStoreId())
                    .build());
        }
        return OrderResponse.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .message("Order Placed Successfully!")
                .totals(pricing.totals())
                .lineItems(lines)
                .promotions(toApplied(pricing.promotionTotals()))
                .build();
    }

    private static List<OrderResponse.AppliedPromotion> toApplied(Map<String, BigDecimal> amounts) {
        return amounts.entrySet().stream()
                .map(e -> new OrderResponse.AppliedPromotion(e.getKey(), e.getValue()))
                .toList();
    }

    private static void requireText(String value, String field) {
        if (isBlank(value)) {
            throw OrderProblemException.validationFailed(field + " is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
