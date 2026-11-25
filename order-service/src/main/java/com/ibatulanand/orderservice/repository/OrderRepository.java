package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    java.util.Optional<Order> findFirstByOrderNumber(String orderNumber);

    java.util.Optional<Order> findFirstByIdempotencyKey(String idempotencyKey);

    java.util.List<Order> findTop200ByTillIdOrderByIdDesc(String tillId);

}
