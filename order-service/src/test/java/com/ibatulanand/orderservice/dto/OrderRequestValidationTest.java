package com.ibatulanand.orderservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        OrderLineItemsDto dto = new OrderLineItemsDto(null, "iphone_13", null, quantity);
        dto.setPrice(price == null ? null : new BigDecimal(price));
        return dto;
    }

    private static Set<ConstraintViolation<OrderRequest>> validate(OrderLineItemsDto... items) {
        return validator.validate(new OrderRequest(new ArrayList<>(List.of(items))), OrderRequest.ListFirst.class);
    }

    @Test
    void acceptsNormalOrder() {
        assertThat(validate(item("1200.00", 1), item("0.99", 3))).isEmpty();
    }

    @Test
    @Timeout(5)
    void normalizesTrailingZerosBeyondCents() throws Exception {
        OrderLineItemsDto dto = new ObjectMapper().readValue(
                "{\"skuCode\":\"iphone_13\",\"price\":1200.000,\"quantity\":1}", OrderLineItemsDto.class);
        assertThat(dto.getPrice()).isEqualTo(new BigDecimal("1200.00"));
        assertThat(validate(dto)).isEmpty();
        assertThat(priceAfterSetter("12.500")).isEqualTo(new BigDecimal("12.50"));
        assertThat(priceAfterSetter("0.000")).isEqualTo(new BigDecimal("0.00"));
        assertThat(priceAfterSetter("1.001")).isEqualTo(new BigDecimal("1.001"));
        assertThat(priceAfterSetter("1e-1000000000")).isEqualTo(new BigDecimal("1e-1000000000"));
        assertThat(priceAfterSetter("1e1000000000")).isEqualTo(new BigDecimal("1e1000000000"));
        assertThat(priceAfterSetter("100000000000000000000.000").scale()).isEqualTo(3);
        assertThat(priceAfterSetter("0e10")).isEqualTo(new BigDecimal("0.00"));
        assertThat(priceAfterSetter("1e3")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(validate(item("0e10", 1))).isEmpty();
        String longZeros = "1." + "0".repeat(999);
        assertThat(priceAfterSetter(longZeros)).isEqualTo(new BigDecimal(longZeros));
    }

    private static BigDecimal priceAfterSetter(String price) {
        OrderLineItemsDto dto = new OrderLineItemsDto();
        dto.setPrice(new BigDecimal(price));
        return dto.getPrice();
    }

    @Test
    @Timeout(5)
    void rejectsHugePositiveExponentWithoutExpandingIt() {
        assertThat(validate(item("1e1000000000", 1)))
                .extracting(v -> v.getPropertyPath().toString())
                .isNotEmpty()
                .containsOnly("orderLineItemsDtoList[0].price");
    }

    @Test
    @Timeout(5)
    void rejectsHugeNegativeExponent() {
        assertThat(validate(item("1e-1000000000", 1))).isNotEmpty();
    }

    @Test
    @Timeout(5)
    void rejectsExponentWhoseDigitCountOverflowsInt() {
        assertThat(validate(item("1e2147483647", 1))).isNotEmpty();
        assertThat(validate(item("10000000000", 1))).isNotEmpty();
        assertThat(validate(item("9999999999.99", 1))).isEmpty();
    }

    @Test
    @Timeout(5)
    void oversizedListIsRejectedWithoutValidatingEachItem() {
        List<OrderLineItemsDto> items = new ArrayList<>(Collections.nCopies(200_000, (OrderLineItemsDto) null));
        assertThat(validator.validate(new OrderRequest(items), OrderRequest.ListFirst.class))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("orderLineItemsDtoList");
    }

    @Test
    @Timeout(5)
    void oversizedListOfInvalidItemsIsRejectedWithoutValidatingEachItem() {
        List<OrderLineItemsDto> items = IntStream.range(0, 200_000).mapToObj(i -> item("-1.001", 0)).collect(Collectors.toList());
        assertThat(validator.validate(new OrderRequest(items), OrderRequest.ListFirst.class))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("orderLineItemsDtoList");
    }

    @Test
    void rejectsNegativeNullAndTooPrecisePrices() {
        assertThat(validate(item("-1.00", 1))).hasSize(1);
        assertThat(validate(item(null, 1))).hasSize(1);
        assertThat(validate(item("1.001", 1))).hasSize(1);
        assertThat(validate(item("12345678901", 1))).isNotEmpty();
    }

    @Test
    void rejectsInvalidQuantityAndSku() {
        assertThat(validate(item("1.00", 0))).hasSize(1);
        assertThat(validate(item("1.00", null))).hasSize(1);
        assertThat(validate(new OrderLineItemsDto(null, " ", BigDecimal.ONE, 1))).hasSize(1);
    }

    @Test
    void rejectsEmptyNullOrOversizedItemList() {
        assertThat(validator.validate(new OrderRequest(null), OrderRequest.ListFirst.class)).hasSize(1);
        assertThat(validator.validate(new OrderRequest(List.of()), OrderRequest.ListFirst.class)).hasSize(1);
        assertThat(validator.validate(new OrderRequest(Collections.singletonList(null)), OrderRequest.ListFirst.class)).hasSize(1);
        assertThat(validator.validate(new OrderRequest(Collections.nCopies(101, item("1.00", 1))), OrderRequest.ListFirst.class)).hasSize(1);
    }
}
