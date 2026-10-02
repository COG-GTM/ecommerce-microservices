package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "eureka.client.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderPersistenceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    OrderRepository orderRepository;

    @Test
    void roundTrip() {
        Order order = new Order();
        order.setOrderNumber("test-" + System.nanoTime());
        order.setStoreId("1969");
        order.setRegisterId("04");
        order.setAssociateId("A-4471");
        order.setStatus("COMPLETED");
        order.setTaxExempt(false);
        order.setCreatedAt(Instant.now());
        order.setMerchandiseTotal(new BigDecimal("159.85"));
        order.setServicesAndFees(new BigDecimal("12.00"));
        order.setDiscountTotal(new BigDecimal("63.54"));
        order.setTaxableSubtotal(new BigDecimal("108.31"));
        order.setTaxRate(new BigDecimal("0.08625"));
        order.setSalesTax(new BigDecimal("9.34"));
        order.setTotal(new BigDecimal("117.65"));
        order.setSavedToday(new BigDecimal("63.54"));
        order.setChangeDue(new BigDecimal("0.00"));

        OrderLineItems line = new OrderLineItems();
        line.setSkuCode("268341-016-L");
        line.setPrice(new BigDecimal("17.97"));
        line.setQuantity(1);
        line.setListPrice(new BigDecimal("29.95"));
        line.setUnitPrice(new BigDecimal("17.97"));
        line.setExtendedPrice(new BigDecimal("17.97"));
        line.setFinalSale(true);
        line.setDescription("Vintage Soft Crewneck Tee");
        order.setOrderLineItemsList(List.of(line));

        OrderPromotion promo = new OrderPromotion();
        promo.setCode("FALL30");
        promo.setDescription("30% off");
        promo.setPercentOff(new BigDecimal("30"));
        promo.setAmountOff(new BigDecimal("0"));
        order.setPromotions(List.of(promo));

        OrderTender tender = new OrderTender();
        tender.setType(TenderType.CREDIT_DEBIT);
        tender.setLabel("CREDIT_DEBIT");
        tender.setAmount(new BigDecimal("117.65"));
        order.setTenders(List.of(tender));

        Order saved = orderRepository.saveAndFlush(order);
        assertNotNull(saved.getId());

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals("1969", reloaded.getStoreId());
        assertEquals(1, reloaded.getOrderLineItemsList().size());
        assertEquals("268341-016-L", reloaded.getOrderLineItemsList().get(0).getSkuCode());
        assertTrue(reloaded.getOrderLineItemsList().get(0).isFinalSale());
        assertEquals(1, reloaded.getPromotions().size());
        assertEquals("FALL30", reloaded.getPromotions().get(0).getCode());
        assertEquals(1, reloaded.getTenders().size());
        assertEquals(TenderType.CREDIT_DEBIT, reloaded.getTenders().get(0).getType());
        assertEquals(0, reloaded.getTotal().compareTo(new BigDecimal("117.65")));
        assertEquals(0, reloaded.getTaxRate().compareTo(new BigDecimal("0.08625")));
    }
}
