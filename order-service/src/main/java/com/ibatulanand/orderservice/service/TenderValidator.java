package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.TenderDto;
import com.ibatulanand.orderservice.exception.OrderValidationException;
import com.ibatulanand.orderservice.model.TenderType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class TenderValidator {

    /**
     * Validates the tenders against the order total and returns the change due
     * (scale 2). Throws {@link OrderValidationException} on any rule violation.
     */
    public BigDecimal validate(List<TenderDto> tenders, BigDecimal total) {
        if (tenders == null || tenders.isEmpty()) {
            throw new OrderValidationException("At least one tender is required");
        }

        BigDecimal sumAll = BigDecimal.ZERO;
        BigDecimal sumNonCash = BigDecimal.ZERO;
        BigDecimal sumCash = BigDecimal.ZERO;

        for (TenderDto tender : tenders) {
            TenderType type = parseType(tender);
            BigDecimal amount = tender.getAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new OrderValidationException("Tender amount must be greater than zero");
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                throw new OrderValidationException("Tender amount must have at most 2 decimal places: " + amount);
            }
            sumAll = sumAll.add(amount);
            if (type == TenderType.CASH) {
                sumCash = sumCash.add(amount);
            } else {
                sumNonCash = sumNonCash.add(amount);
            }
        }

        if (sumNonCash.compareTo(total) > 0) {
            throw new OrderValidationException(
                    "Non-cash tenders (" + money(sumNonCash) + ") exceed the order total (" + money(total) + ")");
        }
        if (sumAll.compareTo(total) < 0) {
            throw new OrderValidationException(
                    "Tenders (" + money(sumAll) + ") do not cover the order total (" + money(total) + ")");
        }
        BigDecimal changeDue = sumAll.subtract(total).setScale(2, RoundingMode.HALF_UP);
        if (changeDue.compareTo(sumCash) > 0) {
            throw new OrderValidationException("Change due exceeds the cash tendered");
        }
        return changeDue;
    }

    private TenderType parseType(TenderDto tender) {
        String raw = tender.getType();
        try {
            return TenderType.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OrderValidationException("Unknown tender type: " + raw);
        }
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
