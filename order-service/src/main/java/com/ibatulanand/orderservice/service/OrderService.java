package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.dto.PromotionDto;
import com.ibatulanand.orderservice.dto.TenderDto;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.model.Promotion;
import com.ibatulanand.orderservice.model.Tender;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final int MONEY_SCALE = 2;

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    /**
     * Prices a basket (totals, promotions, tax, tender validation) without reserving stock or persisting it.
     */
    public OrderResponse priceOrder(OrderRequest orderRequest) {
        return mapToOrderResponse(buildOrder(orderRequest));
    }

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        Order order = buildOrder(orderRequest);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory service, and place order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes)
                                .queryParam("storeId", order.getStoreId())
                                .build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = inventoryResponseArray != null
                && inventoryResponseArray.length == skuCodes.size()
                && Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);

        if (!allProductsInStock) {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }

        orderRepository.save(order);

        // Send order to the kafka topic
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

        return mapToOrderResponse(order);
    }

    private Order buildOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStoreId(orderRequest.getStoreId());
        order.setRegisterId(orderRequest.getRegisterId());
        order.setAssociateId(orderRequest.getAssociateId());
        order.setLane(orderRequest.getLane());
        order.setTaxExempt(orderRequest.isTaxExempt());
        order.setTaxRate(orderRequest.getTaxRate() == null ? BigDecimal.ZERO : orderRequest.getTaxRate());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToLineItem)
                .toList();
        order.setOrderLineItemsList(orderLineItems);
        order.setPromotions(mapToPromotions(orderRequest.getPromotions()));
        order.setTenders(mapToTenders(orderRequest.getTenders()));

        computeTotals(order, orderRequest.getServicesAndFees());
        validateTenders(order);
        return order;
    }

    /**
     * Computes line extensions, merchandise total, promotion discounts, tax and the grand total.
     */
    private void computeTotals(Order order, BigDecimal servicesAndFees) {
        BigDecimal merchandiseTotal = BigDecimal.ZERO;
        BigDecimal lineSavings = BigDecimal.ZERO;

        for (OrderLineItems lineItem : order.getOrderLineItemsList()) {
            BigDecimal quantity = BigDecimal.valueOf(lineItem.getQuantity() == null ? 0 : lineItem.getQuantity());
            BigDecimal unitPrice = lineItem.getUnitPrice() != null ? lineItem.getUnitPrice() : lineItem.getPrice();
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            lineItem.setUnitPrice(unitPrice);
            if (lineItem.getPrice() == null) {
                lineItem.setPrice(unitPrice);
            }

            BigDecimal extendedPrice = money(unitPrice.multiply(quantity));
            lineItem.setExtendedPrice(extendedPrice);
            merchandiseTotal = merchandiseTotal.add(extendedPrice);

            BigDecimal listPrice = lineItem.getListPrice();
            BigDecimal lineDiscount = lineItem.getDiscountAmount();
            if (lineDiscount == null && listPrice != null) {
                lineDiscount = money(listPrice.subtract(unitPrice).multiply(quantity)).max(BigDecimal.ZERO);
                lineItem.setDiscountAmount(lineDiscount);
            }
            if (lineDiscount != null) {
                lineSavings = lineSavings.add(lineDiscount);
            }
        }

        BigDecimal fees = money(servicesAndFees == null ? BigDecimal.ZERO : servicesAndFees);
        BigDecimal promotionDiscount = order.getPromotions() == null ? BigDecimal.ZERO
                : order.getPromotions().stream()
                .map(promotion -> promotion.getAmount() == null ? BigDecimal.ZERO : promotion.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        promotionDiscount = money(promotionDiscount);

        BigDecimal taxableSubtotal = money(merchandiseTotal.add(fees).subtract(promotionDiscount))
                .max(BigDecimal.ZERO);
        BigDecimal salesTax = order.isTaxExempt()
                ? money(BigDecimal.ZERO)
                : money(taxableSubtotal.multiply(order.getTaxRate()));

        order.setMerchandiseTotal(money(merchandiseTotal));
        order.setServicesAndFees(fees);
        order.setDiscountTotal(promotionDiscount);
        order.setTaxableSubtotal(taxableSubtotal);
        order.setSalesTax(salesTax);
        order.setTotal(money(taxableSubtotal.add(salesTax)));
        order.setSavedToday(money(promotionDiscount.add(lineSavings)));
    }

    private void validateTenders(Order order) {
        if (order.getTenders() == null || order.getTenders().isEmpty()) {
            return;
        }
        BigDecimal tendered = order.getTenders().stream()
                .map(tender -> tender.getAmount() == null ? BigDecimal.ZERO : tender.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (money(tendered).compareTo(order.getTotal()) != 0) {
            throw new IllegalArgumentException(
                    "Tendered amount " + money(tendered) + " does not cover order total " + order.getTotal());
        }
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private OrderLineItems mapToLineItem(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setDescription(orderLineItemsDto.getDescription());
        orderLineItems.setColorName(orderLineItemsDto.getColorName());
        orderLineItems.setSize(orderLineItemsDto.getSize());
        orderLineItems.setListPrice(orderLineItemsDto.getListPrice());
        orderLineItems.setUnitPrice(orderLineItemsDto.getUnitPrice());
        orderLineItems.setDiscountAmount(orderLineItemsDto.getDiscountAmount());
        orderLineItems.setDiscountReason(orderLineItemsDto.getDiscountReason());
        return orderLineItems;
    }

    private List<Promotion> mapToPromotions(List<PromotionDto> promotionDtos) {
        if (promotionDtos == null) {
            return List.of();
        }
        return promotionDtos.stream()
                .map(promotionDto -> {
                    Promotion promotion = new Promotion();
                    promotion.setCode(promotionDto.getCode());
                    promotion.setDescription(promotionDto.getDescription());
                    promotion.setAmount(promotionDto.getAmount());
                    return promotion;
                }).toList();
    }

    private List<Tender> mapToTenders(List<TenderDto> tenderDtos) {
        if (tenderDtos == null) {
            return List.of();
        }
        return tenderDtos.stream()
                .map(tenderDto -> {
                    Tender tender = new Tender();
                    tender.setType(tenderDto.getType());
                    tender.setAmount(tenderDto.getAmount());
                    tender.setReference(tenderDto.getReference());
                    return tender;
                }).toList();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderLineItemsDtoList(order.getOrderLineItemsList().stream()
                        .map(lineItem -> OrderLineItemsDto.builder()
                                .id(lineItem.getId())
                                .skuCode(lineItem.getSkuCode())
                                .price(lineItem.getPrice())
                                .quantity(lineItem.getQuantity())
                                .description(lineItem.getDescription())
                                .colorName(lineItem.getColorName())
                                .size(lineItem.getSize())
                                .listPrice(lineItem.getListPrice())
                                .unitPrice(lineItem.getUnitPrice())
                                .extendedPrice(lineItem.getExtendedPrice())
                                .discountAmount(lineItem.getDiscountAmount())
                                .discountReason(lineItem.getDiscountReason())
                                .build())
                        .toList())
                .storeId(order.getStoreId())
                .registerId(order.getRegisterId())
                .associateId(order.getAssociateId())
                .lane(order.getLane())
                .merchandiseTotal(order.getMerchandiseTotal())
                .servicesAndFees(order.getServicesAndFees())
                .discountTotal(order.getDiscountTotal())
                .taxableSubtotal(order.getTaxableSubtotal())
                .taxRate(order.getTaxRate())
                .salesTax(order.getSalesTax())
                .total(order.getTotal())
                .savedToday(order.getSavedToday())
                .taxExempt(order.isTaxExempt())
                .promotions(order.getPromotions().stream()
                        .map(promotion -> PromotionDto.builder()
                                .code(promotion.getCode())
                                .description(promotion.getDescription())
                                .amount(promotion.getAmount())
                                .build())
                        .toList())
                .tenders(order.getTenders().stream()
                        .map(tender -> TenderDto.builder()
                                .type(tender.getType())
                                .amount(tender.getAmount())
                                .reference(tender.getReference())
                                .build())
                        .toList())
                .build();
    }
}
