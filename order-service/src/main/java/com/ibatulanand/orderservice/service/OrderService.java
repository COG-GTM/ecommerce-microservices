package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.client.InventoryClient;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.OutOfStockException;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        List<OrderLineItemsDto> orderLineItemsDtos = OrderRequestValidator.validate(orderRequest);

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderLineItemsDtos
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .distinct()
                .toList();

        // Call Inventory service, and place order if product is in stock
        Set<String> inStockSkuCodes = inventoryClient.getStock(skuCodes).stream()
                .filter(InventoryResponse::isInStock)
                .map(InventoryResponse::getSkuCode)
                .collect(Collectors.toSet());

        List<String> outOfStockSkuCodes = skuCodes.stream()
                .filter(skuCode -> !inStockSkuCodes.contains(skuCode))
                .toList();

        if (!outOfStockSkuCodes.isEmpty()) {
            throw new OutOfStockException(outOfStockSkuCodes);
        }

        orderRepository.save(order);

        // Send order to the kafka topic
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

        return "Order Placed Successfully!";
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
