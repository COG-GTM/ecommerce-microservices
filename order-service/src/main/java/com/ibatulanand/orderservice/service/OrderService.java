package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.client.InventoryClient;
import com.ibatulanand.orderservice.dto.*;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.OutOfStockException;
import com.ibatulanand.orderservice.exception.TaxExemptNotAuthorizedException;
import com.ibatulanand.orderservice.exception.UpstreamUnavailableException;
import com.ibatulanand.orderservice.model.*;
import com.ibatulanand.orderservice.pricing.OrderTotals;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final PricingService pricingService;
    private final TenderValidator tenderValidator;
    private final InventoryClient inventoryClient;

    public OrderResponse placeOrder(OrderRequest orderRequest,
                                    String storeIdHeader,
                                    String registerIdHeader,
                                    String associateIdHeader,
                                    String userRolesHeader) {
        String storeId = PricingService.firstNonBlank(storeIdHeader, orderRequest.getStoreId());
        String registerId = PricingService.firstNonBlank(registerIdHeader, orderRequest.getRegisterId());
        String associateId = PricingService.firstNonBlank(associateIdHeader, orderRequest.getAssociateId());

        if (orderRequest.isTaxExempt() && !hasRole(userRolesHeader, "pos-manager")) {
            throw new TaxExemptNotAuthorizedException("Tax-exempt sales require the pos-manager role");
        }

        QuoteResponse quote = pricingService.quote(orderRequest, storeId);
        OrderTotals totals = quote.totals();

        BigDecimal changeDue = tenderValidator.validate(orderRequest.getTenders(), totals.total());

        checkStock(quote.lineItems().stream().map(PricedLineItem::skuCode).distinct().toList());

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStoreId(storeId);
        order.setRegisterId(registerId);
        order.setAssociateId(associateId);
        order.setStatus("COMPLETED");
        order.setTaxExempt(orderRequest.isTaxExempt());
        order.setCreatedAt(Instant.now());
        order.setMerchandiseTotal(totals.merchandiseTotal());
        order.setServicesAndFees(totals.servicesAndFees());
        order.setDiscountTotal(totals.discountTotal());
        order.setTaxableSubtotal(totals.taxableSubtotal());
        order.setTaxRate(totals.taxRate());
        order.setSalesTax(totals.salesTax());
        order.setTotal(totals.total());
        order.setSavedToday(totals.savedToday());
        order.setChangeDue(changeDue);
        order.setOrderLineItemsList(quote.lineItems().stream().map(this::toEntity).toList());
        order.setPromotions(quote.promotions().stream().map(this::toEntity).toList());
        order.setTenders(orderRequest.getTenders().stream().map(this::toEntity).toList());

        orderRepository.save(order);

        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                "Order placed",
                totals,
                order.getTenders().stream()
                        .map(t -> new TenderDto(t.getType().name(), t.getLabel(), t.getAmount()))
                        .toList(),
                changeDue);
    }

    private void checkStock(List<String> skuCodes) {
        List<InventoryResponse> inventory;
        try {
            inventory = inventoryClient.checkStock(skuCodes).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof UpstreamUnavailableException upstream) {
                throw upstream;
            }
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
        Set<String> inStock = new HashSet<>();
        for (InventoryResponse response : inventory) {
            if (response.isInStock()) {
                inStock.add(response.getSkuCode());
            }
        }
        List<String> outOfStock = skuCodes.stream().filter(sku -> !inStock.contains(sku)).toList();
        if (!outOfStock.isEmpty()) {
            throw new OutOfStockException(outOfStock);
        }
    }

    private boolean hasRole(String userRolesHeader, String role) {
        if (userRolesHeader == null) {
            return false;
        }
        return Arrays.stream(userRolesHeader.split(","))
                .map(String::trim)
                .anyMatch(role::equals);
    }

    private OrderLineItems toEntity(PricedLineItem line) {
        OrderLineItems entity = new OrderLineItems();
        entity.setSkuCode(line.skuCode());
        entity.setPrice(line.unitPrice());
        entity.setQuantity(line.quantity());
        entity.setListPrice(line.listPrice());
        entity.setUnitPrice(line.unitPrice());
        entity.setExtendedPrice(line.extendedPrice());
        entity.setFinalSale(line.finalSale());
        entity.setDescription(line.description());
        return entity;
    }

    private OrderPromotion toEntity(PromotionDto promo) {
        OrderPromotion entity = new OrderPromotion();
        entity.setCode(promo.code());
        entity.setDescription(promo.description());
        entity.setPercentOff(promo.percentOff());
        entity.setAmountOff(promo.amountOff());
        return entity;
    }

    private OrderTender toEntity(TenderDto tender) {
        OrderTender entity = new OrderTender();
        entity.setType(TenderType.valueOf(tender.getType().trim().toUpperCase()));
        entity.setLabel(tender.effectiveLabel());
        entity.setAmount(tender.getAmount());
        return entity;
    }
}
