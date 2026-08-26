package com.ibatulanand.productservice.repository;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.support.AbstractMongoTest;
import com.ibatulanand.productservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest extends AbstractMongoTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void clearProducts() {
        productRepository.deleteAll();
    }

    @Test
    void should_persist_every_field_and_assign_id_when_saving_product() {
        Product product = TestFixtures.product("Iphone 15");
        product.setPrice(new BigDecimal("1500.55"));

        productRepository.save(product);

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(1);
        Product persisted = products.get(0);
        assertThat(persisted.getId()).isNotBlank();
        assertThat(persisted.getName()).isEqualTo("Iphone 15");
        assertThat(persisted.getDescription()).isEqualTo("Description of Iphone 15");
        assertThat(persisted.getPrice()).isEqualByComparingTo(new BigDecimal("1500.55"));
    }
}
