package com.ibatulanand.orderservice.integration;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.OrderService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class InventoryWebClientIntegrationTest {

    private static MockWebServer mockWebServer;

    /**
     * Registers the MockWebServer as the single "inventory-service" instance in Spring Cloud's
     * SimpleDiscoveryClient, so the real @LoadBalanced WebClient.Builder resolves
     * http://inventory-service/... to the mock server.
     */
    @DynamicPropertySource
    static void inventoryServiceInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.inventory-service[0].uri",
                () -> mockWebServer.url("/").toString().replaceAll("/$", ""));
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockBean
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void cleanDb() {
        orderRepository.deleteAll();
    }

    @Test
    void sendsSkuCodesAsQueryParamsAndParsesInventoryResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"skuCode\":\"iphone_13\",\"inStock\":true},{\"skuCode\":\"pixel_8\",\"inStock\":true}]"));

        String result = orderService.placeOrder(orderRequest("iphone_13", "pixel_8"));

        assertThat(result).isEqualTo("Order Placed Successfully!");
        RecordedRequest recorded = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getRequestUrl().encodedPath()).isEqualTo("/api/inventory");
        assertThat(recorded.getRequestUrl().queryParameterValues("skuCode"))
                .containsExactly("iphone_13", "pixel_8");

        transactionTemplate.executeWithoutResult(status -> {
            var orders = orderRepository.findAll();
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getOrderLineItemsList()).hasSize(2);
        });
        verify(kafkaTemplate).send(any(String.class), any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsOrderWhenInventoryReportsOutOfStock() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"skuCode\":\"iphone_13\",\"inStock\":false}]"));

        assertThatThrownBy(() -> orderService.placeOrder(orderRequest("iphone_13")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(mockWebServer.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(orderRepository.findAll()).isEmpty();
        verify(kafkaTemplate, never()).send(any(String.class), any(OrderPlacedEvent.class));
    }

    @Test
    void inventoryResponseJsonDeserializesInStockFlag() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"skuCode\":\"iphone_13\",\"inStock\":true}]"));

        InventoryResponse[] responses = WebClient.builder().build().get()
                .uri(mockWebServer.url("/api/inventory").uri())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(responses).hasSize(1);
        assertThat(responses[0].getSkuCode()).isEqualTo("iphone_13");
        assertThat(responses[0].isInStock()).isTrue();
    }

    private OrderRequest orderRequest(String... skuCodes) {
        List<OrderLineItemsDto> items = java.util.Arrays.stream(skuCodes)
                .map(sku -> new OrderLineItemsDto(null, sku, BigDecimal.TEN, 1))
                .toList();
        return new OrderRequest(items);
    }
}
