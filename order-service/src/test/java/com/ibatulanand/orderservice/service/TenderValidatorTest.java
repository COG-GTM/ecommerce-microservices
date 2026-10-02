package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.TenderDto;
import com.ibatulanand.orderservice.exception.OrderValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TenderValidatorTest {

    private static final BigDecimal TOTAL = new BigDecimal("117.65");

    private final TenderValidator validator = new TenderValidator();

    private static TenderDto tender(String type, String amount) {
        return new TenderDto(type, null, amount == null ? null : new BigDecimal(amount));
    }

    private BigDecimal validate(List<TenderDto> tenders) {
        return validator.validate(tenders, TOTAL);
    }

    private void assert422(List<TenderDto> tenders, String messagePart) {
        OrderValidationException ex =
                assertThrows(OrderValidationException.class, () -> validate(tenders));
        assertTrue(ex.getMessage().contains(messagePart),
                "expected message to contain '" + messagePart + "' but was: " + ex.getMessage());
    }

    @Test
    void exactCardPayment() {
        assertEquals(0, validate(List.of(tender("CREDIT_DEBIT", "117.65"))).compareTo(new BigDecimal("0.00")));
    }

    @Test
    void giftCardPlusCard() {
        assertEquals(0, validate(List.of(
                tender("GIFT_CARD", "50.00"),
                tender("CREDIT_DEBIT", "67.65"))).compareTo(new BigDecimal("0.00")));
    }

    @Test
    void threeWaySplit() {
        assertEquals(0, validate(List.of(
                tender("GIFT_CARD", "50.00"),
                tender("MOBILE_WALLET", "40.00"),
                tender("CREDIT_DEBIT", "27.65"))).compareTo(new BigDecimal("0.00")));
    }

    @Test
    void cashOverpaymentGivesChange() {
        assertEquals(0, validate(List.of(tender("CASH", "120.00"))).compareTo(new BigDecimal("2.35")));
    }

    @Test
    void mixedCashAndCardChange() {
        assertEquals(0, validate(List.of(
                tender("CREDIT_DEBIT", "100.00"),
                tender("CASH", "20.00"))).compareTo(new BigDecimal("2.35")));
    }

    @Test
    void nonCashExceedsTotal() {
        assert422(List.of(tender("CREDIT_DEBIT", "117.66")),
                "exceed the order total");
        assert422(List.of(tender("GIFT_CARD", "100.00"), tender("CREDIT_DEBIT", "20.00")),
                "Non-cash tenders (120.00) exceed the order total (117.65)");
    }

    @Test
    void tendersDoNotCoverTotal() {
        assert422(List.of(tender("CASH", "100.00")),
                "Tenders (100.00) do not cover the order total (117.65)");
        assert422(List.of(tender("CREDIT_DEBIT", "50.00"), tender("CASH", "50.00")),
                "do not cover the order total");
    }

    @Test
    void noTenders() {
        assert422(List.of(), "At least one tender is required");
        assert422(null, "At least one tender is required");
    }

    @Test
    void invalidAmounts() {
        assert422(List.of(tender("CASH", "0")), "greater than zero");
        assert422(List.of(tender("CASH", null)), "greater than zero");
        assert422(List.of(tender("CASH", "-5.00")), "greater than zero");
        assert422(List.of(tender("CASH", "1.005")), "at most 2 decimal places");
    }

    @Test
    void unknownTenderType() {
        assert422(List.of(tender("CHECK", "117.65")), "Unknown tender type: CHECK");
    }
}
