package com.ibatulanand.tillservice;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.pricing.LinePayments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LinePaymentsTest {

    @Test
    void noAdjustmentsPaysTheLinePrice() {
        assertThat(LinePayments.allocate(basket(List.of(), 350, 155))).containsExactly(
                Map.entry("L1", 350), Map.entry("L2", 155));
    }

    @Test
    void savingTiedToALineComesOffThatLine() {
        // More Card: cheddar £3.50 shelf, £3.00 member price.
        Basket basket = basket(List.of(new Adjustment("MORE_CARD", "More Card price", -50, "L1")), 350, 155);
        assertThat(LinePayments.allocate(basket)).containsExactly(Map.entry("L1", 300), Map.entry("L2", 155));
    }

    @Test
    void savingLargerThanItsLineAndUntiedSavingsAreSharedByValue() {
        Basket basket = basket(List.of(new Adjustment("MEAL_DEAL", "Meal deal", -150, "L2"),
                new Adjustment("STAFF", "Staff discount", -30, null)), 350, 100, 50);
        Map<String, Integer> paid = LinePayments.allocate(basket);
        assertThat(paid.get("L2")).isZero();
        assertThat(paid.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(basket.getTotalPence());
        assertThat(paid.values()).allMatch(p -> p >= 0);
    }

    @Test
    void alwaysAddsUpToTheBasketTotal() {
        Basket basket = basket(List.of(new Adjustment("MULTI", "3 for 2", -101, null)), 101, 101, 101);
        Map<String, Integer> paid = LinePayments.allocate(basket);
        assertThat(paid.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(202);
    }

    private static Basket basket(List<Adjustment> adjustments, int... linePence) {
        Basket basket = new Basket();
        int total = 0;
        for (int i = 0; i < linePence.length; i++) {
            basket.getLines().add(new BasketLine("L" + (i + 1), "b" + i, "SKU" + i, "Item " + i, 1, linePence[i],
                    linePence[i]));
            total += linePence[i];
        }
        basket.setAdjustments(adjustments);
        basket.setTotalPence(Math.max(0, total + adjustments.stream().mapToInt(Adjustment::amountPence).sum()));
        return basket;
    }
}
