package com.ibatulanand.orderservice;

import com.ibatulanand.orderservice.controller.OrderController;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private OrderController orderController;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Test
	void contextLoads() {
		assertThat(orderController).isNotNull();
		assertThat(orderService).isNotNull();
		assertThat(orderRepository).isNotNull();
	}
}
