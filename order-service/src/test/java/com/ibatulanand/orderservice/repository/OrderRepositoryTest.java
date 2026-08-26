package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.support.AbstractMySqlTest;
import com.ibatulanand.orderservice.support.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryTest extends AbstractMySqlTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void should_cascade_line_items_when_order_saved() {
        Order saved = orderRepository.save(TestFixtures.order("iphone_13", "iphone_13_red"));

        entityManager.flush();
        entityManager.clear();

        Optional<Order> reloaded = orderRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        List<OrderLineItems> lineItems = reloaded.get().getOrderLineItemsList();
        assertThat(lineItems).hasSize(2);
        assertThat(lineItems).allSatisfy(lineItem -> assertThat(lineItem.getId()).isNotNull());
        assertThat(lineItems).extracting(OrderLineItems::getSkuCode)
                .containsExactlyInAnyOrder("iphone_13", "iphone_13_red");
    }

    @Test
    void should_return_line_item_price_and_quantity_when_order_reloaded() {
        Order order = new Order();
        order.setOrderNumber("order-1");
        order.setOrderLineItemsList(List.of(TestFixtures.orderLineItems("iphone_13", 7)));
        Order saved = orderRepository.save(order);

        entityManager.flush();
        entityManager.clear();

        OrderLineItems reloaded = orderRepository.findById(saved.getId())
                .orElseThrow()
                .getOrderLineItemsList()
                .get(0);
        assertThat(reloaded.getSkuCode()).isEqualTo("iphone_13");
        assertThat(reloaded.getQuantity()).isEqualTo(7);
        assertThat(reloaded.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }
}
