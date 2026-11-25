package com.ibatulanand.orderservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static OrderLineItemsDto item(String price, Integer quantity) {
        return new OrderLineItemsDto(null, "iphone_13", price == null ? null : new BigDecimal(price), quantity);
    }

    private static Set<ConstraintViolation<OrderRequest>> validate(OrderLineItemsDto... items) {
        return validator.validate(new OrderRequest(new ArrayList<>(List.of(items))));
    }

    @Test
    void acceptsNormalOrder() {
        assertThat(validate(item("1200.00", 1), item("0.99", 3))).isEmpty();
    }

    @Test
    @Timeout(5)
    void rejectsHugePositiveExponentWithoutExpandingIt() {
        assertThat(validate(item("1e1000000000", 1)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("orderLineItemsDtoList[0].price");
    }

    @Test
    @Timeout(5)
    void rejectsHugeNegativeExponent() {
        assertThat(validate(item("1e-1000000000", 1))).isNotEmpty();
    }

    @Test
    void rejectsNegativeNullAndTooPrecisePrices() {
        assertThat(validate(item("-1.00", 1))).hasSize(1);
        assertThat(validate(item(null, 1))).hasSize(1);
        assertThat(validate(item("1.001", 1))).hasSize(1);
        assertThat(validate(item("12345678901", 1))).hasSize(1);
    }

    @Test
    void rejectsInvalidQuantityAndSku() {
        assertThat(validate(item("1.00", 0))).hasSize(1);
        assertThat(validate(item("1.00", null))).hasSize(1);
        assertThat(validate(new OrderLineItemsDto(null, " ", BigDecimal.ONE, 1))).hasSize(1);
    }

    @Test
    void rejectsEmptyNullOrOversizedItemList() {
        assertThat(validator.validate(new OrderRequest(null))).hasSize(1);
        assertThat(validator.validate(new OrderRequest(List.of()))).hasSize(1);
        assertThat(validator.validate(new OrderRequest(Collections.singletonList(null)))).hasSize(1);
        assertThat(validator.validate(new OrderRequest(Collections.nCopies(101, item("1.00", 1))))).hasSize(1);
    }
}
