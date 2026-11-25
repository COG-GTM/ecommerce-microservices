package com.ibatulanand.productservice.seed;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MBA-3932: every seeded product carries a UK VAT code; at least two barcodes are standard-rated. */
class GroceryCatalogueSeederVatTest {

    @Test
    void seedsVatCodesAndStandardRatedBarcodes() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findFirstByBarcode(anyString())).thenReturn(Optional.empty());

        new GroceryCatalogueSeeder(repository).run(null);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        List<Product> products = saved.getAllValues();
        assertThat(products).allSatisfy(p -> assertThat(p.getVatCode()).isIn("Z", "S"));
        Map<String, String> vatByBarcode = products.stream()
                .collect(Collectors.toMap(Product::getBarcode, Product::getVatCode));
        assertThat(vatByBarcode).containsEntry("1001", "Z").containsEntry("4001", "S").containsEntry("4002", "S");
        assertThat(vatByBarcode.values().stream().filter("S"::equals).count()).isGreaterThanOrEqualTo(2);
        assertThat(products).filteredOn(p -> p.getBarcode().startsWith("4"))
                .allSatisfy(p -> assertThat(p.getQuickKey()).isFalse());
    }
}
