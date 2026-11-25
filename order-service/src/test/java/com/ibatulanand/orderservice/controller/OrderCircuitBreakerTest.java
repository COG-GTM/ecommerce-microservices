package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.client.InventoryClient;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = OrderCircuitBreakerTest.TestApplication.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "management.tracing.enabled=false"
        })
@AutoConfigureMockMvc
class OrderCircuitBreakerTest {

    private static final String IN_STOCK = "iphone_15";
    private static final String SOLD_OUT = "iphone_15_pro";

    private static final AtomicBoolean inventoryDown = new AtomicBoolean();
    private static final AtomicInteger inventoryCalls = new AtomicInteger();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            KafkaAutoConfiguration.class
    })
    @Import({OrderController.class, OrderExceptionHandler.class, OrderService.class, InventoryClient.class})
    static class TestApplication {

        @Bean
        WebClient.Builder inventoryStubWebClientBuilder() {
            return WebClient.builder().exchangeFunction(request -> {
                inventoryCalls.incrementAndGet();
                if (inventoryDown.get()) {
                    return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
                }
                List<String> skuCodes = request.url().getQuery() == null ? List.of()
                        : List.of(request.url().getQuery().split("&"));
                String body = skuCodes.stream()
                        .map(param -> param.substring(param.indexOf('=') + 1))
                        .filter(sku -> sku.equals(IN_STOCK) || sku.equals(SOLD_OUT))
                        .map(sku -> "{\"skuCode\":\"" + sku + "\",\"inStock\":" + sku.equals(IN_STOCK) + "}")
                        .collect(Collectors.joining(",", "[", "]"));
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build());
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = circuitBreakerRegistry.circuitBreaker("inventory");
        breaker.reset();
        inventoryDown.set(false);
        inventoryCalls.set(0);
        clearInvocations(orderRepository, kafkaTemplate);
    }

    @Test
    void soldOutOrdersDoNotOpenBreakerForOtherCustomers() throws Exception {
        for (int i = 0; i < 10; i++) {
            order(SOLD_OUT, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.skuCodes[0]").value(SOLD_OUT));
        }

        order(IN_STOCK, 1).andExpect(status().isCreated());

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    void malformedOrdersAreRejectedWithoutTouchingInventoryOrBreaker() throws Exception {
        List<String> badBodies = List.of(
                "{}",
                "{\"orderLineItemsDtoList\":[]}",
                "{\"orderLineItemsDtoList\":[null]}",
                line("{x}", 1),
                line("iphone_15", 0),
                "{\"orderLineItemsDtoList\":[{\"skuCode\":\"iphone_15\",\"quantity\":1}]}"
        );
        for (int round = 0; round < 3; round++) {
            for (String body : badBodies) {
                postOrder(body).andExpect(status().isBadRequest());
            }
        }

        assertThat(inventoryCalls).hasValue(0);
        order(IN_STOCK, 1).andExpect(status().isCreated());
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void unknownSkuIsRejectedAsOutOfStock() throws Exception {
        order("not_a_product", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.skuCodes[0]").value("not_a_product"));

        verify(orderRepository, never()).save(any());
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void inventoryOutageOpensBreakerAndAnswers503() throws Exception {
        inventoryDown.set(true);

        for (int i = 0; i < 3; i++) {
            order(IN_STOCK, 1).andExpect(status().isServiceUnavailable());
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBefore = inventoryCalls.get();
        order(IN_STOCK, 1).andExpect(status().isServiceUnavailable());
        assertThat(inventoryCalls).hasValue(callsBefore);
        verify(orderRepository, never()).save(any());
    }

    private ResultActions order(String skuCode, int quantity) throws Exception {
        return postOrder(line(skuCode, quantity));
    }

    private ResultActions postOrder(String body) throws Exception {
        return mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String line(String skuCode, int quantity) {
        return "{\"orderLineItemsDtoList\":[{\"skuCode\":\"" + skuCode + "\",\"price\":999,\"quantity\":" + quantity + "}]}";
    }
}
