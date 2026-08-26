package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.DbTestBase;
import com.ibatulanand.orderservice.TestHelper;
import com.ibatulanand.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRepositoryTest extends DbTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void should_save_order_with_line_items() {
        Order order = TestHelper.orderFixture("order-1", "iphone_15", "iphone_15_pro");

        Order saved = orderRepository.save(order);

        assertNotNull(saved.getId());
        Optional<Order> found = orderRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("order-1", found.get().getOrderNumber());
        assertEquals(2, found.get().getOrderLineItemsList().size());
    }

    @Test
    void should_find_all_saved_orders() {
        orderRepository.saveAll(List.of(
                TestHelper.orderFixture("order-1", "iphone_15"),
                TestHelper.orderFixture("order-2", "iphone_15_pro")));

        assertEquals(2, orderRepository.findAll().size());
    }
}
