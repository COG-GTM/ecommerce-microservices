package com.ibatulanand.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"management.zipkin.tracing.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceApplicationTests {

	@Container
	@ServiceConnection
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

	@Test
	void contextLoads() {
	}

}
