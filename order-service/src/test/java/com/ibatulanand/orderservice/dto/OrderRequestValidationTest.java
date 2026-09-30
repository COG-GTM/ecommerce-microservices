package com.ibatulanand.orderservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void acceptsWellFormedRequest() {
        assertTrue(validator.validate(request(new OrderLineItemsDto("iphone_15", 1))).isEmpty());
    }

    @Test
    void rejectsNonPositiveOrExcessiveQuantity() {
        assertFalse(validator.validate(request(new OrderLineItemsDto("iphone_15", 0))).isEmpty());
        assertFalse(validator.validate(request(new OrderLineItemsDto("iphone_15", -5))).isEmpty());
        assertFalse(validator.validate(request(new OrderLineItemsDto("iphone_15", null))).isEmpty());
        assertFalse(validator.validate(request(new OrderLineItemsDto("iphone_15", 1001))).isEmpty());
    }

    @Test
    void rejectsBlankOrMalformedSku() {
        assertFalse(validator.validate(request(new OrderLineItemsDto(" ", 1))).isEmpty());
        assertFalse(validator.validate(request(new OrderLineItemsDto(null, 1))).isEmpty());
        assertFalse(validator.validate(request(new OrderLineItemsDto("a&b=c", 1))).isEmpty());
    }

    @Test
    void rejectsEmptyNullOrOversizedLineList() {
        assertFalse(validator.validate(new OrderRequest(List.of())).isEmpty());
        assertFalse(validator.validate(new OrderRequest(null)).isEmpty());
        assertFalse(validator.validate(new OrderRequest(Collections.singletonList(null))).isEmpty());

        List<OrderLineItemsDto> tooMany = new ArrayList<>();
        for (int i = 0; i <= OrderRequest.MAX_LINE_ITEMS; i++) {
            tooMany.add(new OrderLineItemsDto("sku_" + i, 1));
        }
        assertFalse(validator.validate(new OrderRequest(tooMany)).isEmpty());
    }

    private static OrderRequest request(OrderLineItemsDto line) {
        return new OrderRequest(List.of(line));
    }
}
