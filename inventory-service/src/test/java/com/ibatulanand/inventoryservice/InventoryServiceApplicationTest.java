package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InventoryServiceApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            InventoryServiceApplication.main(args);
            spring.verify(() -> SpringApplication.run(InventoryServiceApplication.class, args));
        }
    }

    @Test
    void loadDataSeedsInStockAndOutOfStockSkus() throws Exception {
        InventoryRepository repository = mock(InventoryRepository.class);

        new InventoryServiceApplication().loadData(repository).run();

        ArgumentCaptor<Inventory> saved = ArgumentCaptor.forClass(Inventory.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(Inventory::getSkuCode, Inventory::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", 100),
                        org.assertj.core.groups.Tuple.tuple("iphone_15_pro", 0));
    }
}
