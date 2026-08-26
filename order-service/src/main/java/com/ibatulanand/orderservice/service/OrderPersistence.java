package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderPersistence {

    @Inject
    OrderRepository orderRepository;

    @Transactional
    public void persist(Order order) {
        orderRepository.persist(order);
    }
}
