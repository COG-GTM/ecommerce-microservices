package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.StockReservationItem;
import com.ibatulanand.orderservice.dto.StockReservationRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    static final int MAX_LINE_ITEMS = 100;
    static final int MAX_QUANTITY_PER_SKU = 1000;

    private static final String RESERVATIONS_URI = "http://inventory-service/api/inventory/reservations";

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        List<StockReservationItem> reservation = validate(orderRequest);

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        reserveStock(reservation);
        try {
            orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseStock(reservation);
            throw e;
        }

        // Send order to the kafka topic; the order is already committed, so a failure must not trigger a retry
        try {
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
        } catch (RuntimeException e) {
            log.error("Failed to publish OrderPlacedEvent for order {}", order.getOrderNumber(), e);
        }

        return "Order Placed Successfully!";
    }

    private static List<StockReservationItem> validate(OrderRequest orderRequest) {
        List<OrderLineItemsDto> lines = orderRequest == null ? null : orderRequest.getOrderLineItemsDtoList();
        if (lines == null || lines.isEmpty()) {
            throw badRequest("Order must contain at least one item");
        }
        if (lines.size() > MAX_LINE_ITEMS) {
            throw badRequest("Order must contain at most " + MAX_LINE_ITEMS + " items");
        }
        Map<String, Integer> quantityBySku = new LinkedHashMap<>();
        Map<String, Integer> quantityByFoldedSku = new LinkedHashMap<>();
        for (OrderLineItemsDto line : lines) {
            if (line == null || line.getSkuCode() == null || line.getSkuCode().isBlank()) {
                throw badRequest("Every item needs a skuCode");
            }
            Integer quantity = line.getQuantity();
            if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY_PER_SKU) {
                throw badRequest("Quantity must be between 1 and " + MAX_QUANTITY_PER_SKU);
            }
            quantityBySku.merge(line.getSkuCode(), quantity, Integer::sum);
            int total = quantityByFoldedSku.merge(line.getSkuCode().toLowerCase(Locale.ROOT), quantity, Integer::sum);
            if (total > MAX_QUANTITY_PER_SKU) {
                throw badRequest("Quantity must be between 1 and " + MAX_QUANTITY_PER_SKU);
            }
        }
        return quantityBySku.entrySet().stream()
                .map(entry -> new StockReservationItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void reserveStock(List<StockReservationItem> items) {
        webClientBuilder.build().post()
                .uri(RESERVATIONS_URI)
                .bodyValue(new StockReservationRequest(items))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(),
                        response -> Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Product is not in stock, please try again later")))
                .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(),
                        response -> Mono.error(badRequest("Inventory rejected the order")))
                .toBodilessEntity()
                .block();
    }

    private void releaseStock(List<StockReservationItem> items) {
        try {
            webClientBuilder.build().post()
                    .uri(RESERVATIONS_URI + "/release")
                    .bodyValue(new StockReservationRequest(items))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (RuntimeException e) {
            log.error("Failed to release reserved stock {}", items, e);
        }
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
