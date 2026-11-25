package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    java.util.Optional<Order> findFirstByOrderNumber(String orderNumber);

    java.util.Optional<Order> findFirstByIdempotencyKey(String idempotencyKey);

    java.util.List<Order> findByTransactionId(String transactionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Order> findWithLockByOrderNumber(String orderNumber);

    java.util.List<Order> findByTransactionIdOrderByIdDesc(String transactionId);

    java.util.List<Order> findByOriginalOrderNumber(String originalOrderNumber);

    java.util.List<Order> findByTillIdOrderByIdAsc(String tillId);

}
