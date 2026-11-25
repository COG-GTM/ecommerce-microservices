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
    void mealDealSavingStaysOnTheMealDealItems() {
        // Sandwich £3.00 + crisps £0.85 + water £0.90 for £3.50, anchored on the water; cheddar £3.50 is not in it.
        Basket basket = basket(List.of(new Adjustment("MEAL_DEAL", "Meal deal", -125, "L3", List.of("L1", "L2", "L3"))),
                300, 85, 90, 350);
        Map<String, Integer> paid = LinePayments.allocate(basket);
        assertThat(paid.get("L4")).isEqualTo(350);
        assertThat(paid.get("L1") + paid.get("L2") + paid.get("L3")).isEqualTo(350);
        assertThat(paid).containsEntry("L1", 221).containsEntry("L2", 62).containsEntry("L3", 67);
    }

    @Test
    void savingLargerThanItsAnchorLineDoesNotSpillOntoOtherLines() {
        Basket basket = basket(List.of(new Adjustment("MEAL_DEAL", "Meal deal", -150, "L2", List.of("L2", "L3"))),
                350, 100, 100);
        assertThat(LinePayments.allocate(basket)).containsExactly(
                Map.entry("L1", 350), Map.entry("L2", 25), Map.entry("L3", 25));
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
