package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.StockReservationItem;
import com.ibatulanand.inventoryservice.exception.InsufficientStockException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.sql.init.mode=never"
})
@Import(InventoryService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void seed() {
        inventoryRepository.deleteAll();
        inventoryRepository.save(new Inventory(null, "iphone_15", 100));
        inventoryRepository.save(new Inventory(null, "iphone_15_pro", 0));
        inventoryRepository.save(new Inventory(null, "last_unit", 1));
    }

    @Test
    void isInStockReportsUnknownSkusAsOutOfStock() {
        List<InventoryResponse> responses = inventoryService.isInStock(List.of("iphone_15", "ghost-item"));

        assertThat(responses).extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", true),
                        org.assertj.core.groups.Tuple.tuple("ghost-item", false));
    }

    @Test
    void reserveDecrementsStock() {
        inventoryService.reserve(List.of(new StockReservationItem("iphone_15", 30)));

        assertThat(quantityOf("iphone_15")).isEqualTo(70);
    }

    @Test
    void reserveRejectsUnknownSku() {
        assertThatThrownBy(() -> inventoryService.reserve(List.of(new StockReservationItem("ghost-item", 1))))
                .isInstanceOf(InsufficientStockException.class)
                .extracting("unavailableSkuCodes").isEqualTo(List.of("ghost-item"));
    }

    @Test
    void reserveRejectsQuantityAboveStockWithoutChangingIt() {
        assertThatThrownBy(() -> inventoryService.reserve(List.of(new StockReservationItem("iphone_15", 101))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(quantityOf("iphone_15")).isEqualTo(100);
    }

    @Test
    void reserveIsAllOrNothing() {
        assertThatThrownBy(() -> inventoryService.reserve(List.of(
                new StockReservationItem("iphone_15", 10),
                new StockReservationItem("iphone_15_pro", 1))))
                .isInstanceOf(InsufficientStockException.class)
                .extracting("unavailableSkuCodes").isEqualTo(List.of("iphone_15_pro"));

        assertThat(quantityOf("iphone_15")).isEqualTo(100);
    }

    @Test
    void reserveSumsDuplicateLines() {
        assertThatThrownBy(() -> inventoryService.reserve(List.of(
                new StockReservationItem("last_unit", 1),
                new StockReservationItem("last_unit", 1))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(quantityOf("last_unit")).isEqualTo(1);
    }

    @Test
    void reserveRejectsInvalidRequests() {
        assertThatThrownBy(() -> inventoryService.reserve(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserve(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserve(List.of(new StockReservationItem("iphone_15", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserve(List.of(new StockReservationItem("iphone_15", -5))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserve(List.of(new StockReservationItem(" ", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserve(List.of(
                new StockReservationItem("iphone_15", 600), new StockReservationItem("IPHONE_15", 600))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(quantityOf("iphone_15")).isEqualTo(100);
    }

    @Test
    void releaseReturnsStock() {
        inventoryService.reserve(List.of(new StockReservationItem("iphone_15", 5)));
        inventoryService.release(List.of(new StockReservationItem("iphone_15", 5)));

        assertThat(quantityOf("iphone_15")).isEqualTo(100);
    }

    @Test
    void parallelReservationsForTheLastUnitSucceedExactlyOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> attempt = () -> {
                    start.await();
                    try {
                        inventoryService.reserve(List.of(new StockReservationItem("last_unit", 1)));
                        return true;
                    } catch (InsufficientStockException e) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(quantityOf("last_unit")).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    private int quantityOf(String skuCode) {
        return inventoryRepository.findBySkuCodeOrderByIdAsc(skuCode).stream()
                .mapToInt(Inventory::getQuantity)
                .sum();
    }
}
