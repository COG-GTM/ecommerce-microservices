package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        // Age approvals are only recorded by the till sale route, after a till-side Challenge 25 decision.
        orderRequest.getOrderLineItemsDtoList().forEach(line -> {
            line.setMinimumAge(null);
            line.setAgeApprovedBy(null);
        });
        rejectAgeRestrictedItems(orderRequest);
        placeAndReturnOrder(orderRequest);
        return "Order Placed Successfully!";
    }

    // Until online age verification exists, restricted SKUs can only be sold through a till sale.
    private void rejectAgeRestrictedItems(OrderRequest orderRequest) {
        ProductView[] products = webClientBuilder.build().get()
                .uri("http://product-service/api/product")
                .retrieve()
                .bodyToMono(ProductView[].class)
                .block();
        Set<String> restricted = Arrays.stream(products == null ? new ProductView[0] : products)
                .filter(p -> p.minimumAge() != null && p.minimumAge() > 0)
                .map(ProductView::skuCode)
                .collect(Collectors.toSet());
        List<String> requested = orderRequest.getOrderLineItemsDtoList().stream()
                .map(OrderLineItemsDto::getSkuCode)
                .filter(restricted::contains)
                .distinct()
                .toList();
        if (!requested.isEmpty()) {
            throw new AgeRestrictedItemException(requested);
        }
    }

    record ProductView(String skuCode, Integer minimumAge) {
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orderRepository.findFirstByOrderNumber(orderNumber).map(order -> {
            order.getOrderLineItemsList().size();
            return order;
        });
    }

    public Order placeAndReturnOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setChannel(orderRequest.getChannel());
        order.setStoreNumber(orderRequest.getStoreNumber());
        order.setTillId(orderRequest.getTillId());
        order.setTransactionId(orderRequest.getTransactionId());
        order.setTenderType(orderRequest.getTenderType());
        order.setTotalPence(orderRequest.getTotalPence());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory service, and place order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);

            // Send order to the kafka topic
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber(),
                    order.getChannel(), order.getStoreNumber(), order.getTillId(), order.getTransactionId(),
                    order.getTenderType(), order.getTotalPence()));

            return order;
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        orderLineItems.setMinimumAge(orderLineItemsDto.getMinimumAge());
        orderLineItems.setAgeApprovedBy(orderLineItemsDto.getAgeApprovedBy());
        return orderLineItems;
    }
}
