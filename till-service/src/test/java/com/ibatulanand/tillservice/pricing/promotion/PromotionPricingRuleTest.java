package com.ibatulanand.tillservice.pricing.promotion;

import com.ibatulanand.tillservice.domain.Adjustment;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.pricing.BasketPricer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionPricingRuleTest {

    static final String CHICKEN_SANDWICH = "393401";
    static final String EGG_SANDWICH = "393402";
    static final String CRISPS = "393403";
    static final String FRUIT = "393404";
    static final String WATER = "393405";
    static final String COLA = "393406";
    static final String YOGHURT = "393407";

    static final Promotion YOGHURT_3_FOR_350 = new Promotion.MultiBuy("MB-YOG", "Greek yoghurt 3 for £3.50",
            Set.of(YOGHURT), 3, 350);
    static final Promotion DRINKS_2_FOR_200 = new Promotion.MultiBuy("MB-DRINKS", "Soft drinks 2 for £2.00",
            Set.of(WATER, COLA), 2, 200);
    static final Promotion MEAL_DEAL = new Promotion.MealDeal("MEAL", "Meal deal £3.50",
            Set.of(CHICKEN_SANDWICH, EGG_SANDWICH), Set.of(CRISPS, FRUIT), Set.of(WATER, COLA), 350);

    final BasketPricer pricer = new BasketPricer(List.of(new PromotionPricingRule(
            List.of(YOGHURT_3_FOR_350, DRINKS_2_FOR_200, MEAL_DEAL))));

    @Test
    void multiBuyTriggersOnTheThirdUnitOnly() {
        Basket basket = basket(line("l-yog", YOGHURT, 2, 150));
        pricer.price(basket);
        assertThat(basket.getAdjustments()).isEmpty();
        assertThat(basket.getTotalPence()).isEqualTo(300);

        basket.getLines().get(0).setQuantity(3);
        pricer.price(basket);
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MULTIBUY", "Greek yoghurt 3 for £3.50", -100, "l-yog"));
        assertThat(basket.getSavingsPence()).isEqualTo(100);
        assertThat(basket.getTotalPence()).isEqualTo(350);
    }

    @Test
    void repeatApplicationsAreCombinedAndLeftoverUnitsPayFullPrice() {
        Basket basket = pricer.price(basket(line("l-yog", YOGHURT, 7, 150)));
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MULTIBUY", "Greek yoghurt 3 for £3.50 x2", -200, "l-yog"));
        assertThat(basket.getTotalPence()).isEqualTo(7 * 150 - 200);
    }

    @Test
    void voidingAQualifyingLineRemovesTheSaving() {
        Basket basket = pricer.price(basket(line("l-main", CHICKEN_SANDWICH, 1, 300), line("l-side", CRISPS, 1, 85),
                line("l-drink", COLA, 1, 135)));
        assertThat(basket.getSavingsPence()).isEqualTo(170);

        basket.getLines().remove(1);
        pricer.price(basket);
        assertThat(basket.getAdjustments()).isEmpty();
        assertThat(basket.getTotalPence()).isEqualTo(435);
    }

    @Test
    void mealDealSavingSitsUnderTheLastQualifyingLine() {
        Basket basket = pricer.price(basket(line("l-drink", COLA, 1, 135), line("l-main", CHICKEN_SANDWICH, 1, 300),
                line("l-milk", "1001", 1, 155), line("l-side", CRISPS, 1, 85)));
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MEAL_DEAL", "Meal deal £3.50", -170, "l-side", List.of("l-drink", "l-main", "l-side")));
        assertThat(basket.getTotalPence()).isEqualTo(135 + 300 + 155 + 85 - 170);
    }

    @Test
    void itemQualifyingForTwoOffersGoesToTheBetterOne() {
        // Meal deal saves 170 using one cola; 2 colas for £2 saves only 70. The cola cannot count twice.
        Basket basket = pricer.price(basket(line("l-main", CHICKEN_SANDWICH, 1, 300), line("l-side", CRISPS, 1, 85),
                line("l-cola", COLA, 2, 135)));
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MEAL_DEAL", "Meal deal £3.50", -170, "l-cola", List.of("l-main", "l-side", "l-cola")));
        assertThat(basket.getTotalPence()).isEqualTo(300 + 85 + 270 - 170);
    }

    @Test
    void combinesOffersWhenThatBeatsAnySingleOffer() {
        // One cola in the meal deal (170) plus the other two colas as 2 for £2 (70) = 240.
        Basket basket = pricer.price(basket(line("l-main", CHICKEN_SANDWICH, 1, 300), line("l-side", CRISPS, 1, 85),
                line("l-cola", COLA, 3, 135)));
        assertThat(basket.getAdjustments()).containsExactlyInAnyOrder(
                new Adjustment("MULTIBUY", "Soft drinks 2 for £2.00", -70, "l-cola"),
                new Adjustment("MEAL_DEAL", "Meal deal £3.50", -170, "l-cola", List.of("l-main", "l-side", "l-cola")));
        assertThat(basket.getSavingsPence()).isEqualTo(240);
    }

    @Test
    void bestPriceBeatsGreedyHighestSavingFirst() {
        Promotion mealDeal = new Promotion.MealDeal("MEAL", "Meal deal", Set.of(EGG_SANDWICH), Set.of(CRISPS),
                Set.of(WATER, COLA), 350);
        Promotion colaPair = new Promotion.MultiBuy("COLA2", "Cola 2 for £1.50", Set.of(COLA), 2, 150);
        BasketPricer custom = new BasketPricer(List.of(new PromotionPricingRule(List.of(mealDeal, colaPair))));
        // Greedy: deal with cola saves 250+85+135-350=120, leaving 1 cola -> 120 total.
        // Best: deal with water saves 250+85+90-350=75, plus cola pair saves 270-150=120 -> 195 total.
        Basket basket = custom.price(basket(line("l-egg", EGG_SANDWICH, 1, 250), line("l-crisps", CRISPS, 1, 85),
                line("l-water", WATER, 1, 90), line("l-cola", COLA, 2, 135)));
        assertThat(basket.getSavingsPence()).isEqualTo(195);
        assertThat(basket.getAdjustments()).containsExactlyInAnyOrder(
                new Adjustment("MEAL_DEAL", "Meal deal", -75, "l-water", List.of("l-egg", "l-crisps", "l-water")),
                new Adjustment("MULTIBUY", "Cola 2 for £1.50", -120, "l-cola"));
    }

    @Test
    void offerThatWouldCostTheCustomerMoreIsNotApplied() {
        // Two waters cost 180, less than the 2 for £2 price.
        Basket basket = pricer.price(basket(line("l-water", WATER, 2, 90)));
        assertThat(basket.getAdjustments()).isEmpty();
        assertThat(basket.getTotalPence()).isEqualTo(180);
    }

    @Test
    void mixAndMatchMultiBuyAcrossLines() {
        Basket basket = pricer.price(basket(line("l-water", WATER, 1, 90), line("l-cola", COLA, 1, 135)));
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MULTIBUY", "Soft drinks 2 for £2.00", -25, "l-cola", List.of("l-water", "l-cola")));
    }

    @Test
    void largeBasketsFallBackToGreedyWithoutFailing() {
        Basket basket = pricer.price(basket(line("l-chk", CHICKEN_SANDWICH, 40, 300), line("l-egg", EGG_SANDWICH, 40, 250),
                line("l-crisps", CRISPS, 40, 85), line("l-fruit", FRUIT, 40, 120), line("l-water", WATER, 40, 90),
                line("l-cola", COLA, 40, 135)));
        assertThat(basket.getSavingsPence()).isPositive();
        assertThat(basket.getTotalPence()).isEqualTo(basket.getSubtotalPence() - basket.getSavingsPence());
    }

    @Test
    void veryLargeQuantityOfOneItemDoesNotOverflowTheSearch() {
        Basket basket = pricer.price(basket(line("l-yog", YOGHURT, 30_000, 150)));
        assertThat(basket.getSavingsPence()).isEqualTo(10_000 * 100);
    }

    @Test
    void bulkQuantityIsPricedWithoutOneApplicationPerBundle() {
        Basket basket = pricer.price(basket(line("l-yog", YOGHURT, 3_000_000, 150)));
        assertThat(basket.getAdjustments()).containsExactly(
                new Adjustment("MULTIBUY", "Greek yoghurt 3 for £3.50 x1000000", -100_000_000, "l-yog"));
        assertThat(new PromotionEngine(List.of(YOGHURT_3_FOR_350))
                .bestApplications(List.of(line("l-yog", YOGHURT, 3_000_000, 150))))
                .singleElement().extracting(PromotionEngine.Application::times).isEqualTo(1_000_000);
    }

    @Test
    void unrelatedOffersDoNotChangeEachOthersBestPrice() {
        // Greedy takes the £1.80 bundle and strands W; the exact best is the two £1.00 pairs.
        Promotion bigBundle = new Promotion.MealDeal("BIG", "X+Y+Z for £1.20", Set.of("X"), Set.of("Y"), Set.of("Z"), 120);
        Promotion xw = new Promotion.MultiBuy("XW", "X/W 2 for £1.00", Set.of("X", "W"), 2, 100);
        Promotion yz = new Promotion.MultiBuy("YZ", "Y/Z 2 for £1.00", Set.of("Y", "Z"), 2, 100);
        BasketPricer custom = new BasketPricer(List.of(new PromotionPricingRule(
                List.of(bigBundle, xw, yz, YOGHURT_3_FOR_350))));
        Basket basket = custom.price(basket(line("l-x", "X", 1, 100), line("l-y", "Y", 1, 100),
                line("l-z", "Z", 1, 100), line("l-w", "W", 1, 100), line("l-yog", YOGHURT, 30_000, 150)));
        assertThat(basket.getSavingsPence()).isEqualTo(200 + 10_000 * 100);
    }

    @Test
    void offerWithoutAPriceIsRejected() {
        PromotionProperties properties = new PromotionProperties(List.of(new PromotionProperties.MultiBuyConfig(
                "MB-NO-PRICE", "No price", Set.of(YOGHURT), 3, null)), null);
        assertThatThrownBy(properties::promotions).hasMessageContaining("MB-NO-PRICE").hasMessageContaining("price-pence");
    }

    @Test
    void basketWithoutPromotedItemsHasNoAdjustments() {
        Basket basket = pricer.price(basket(line("l-milk", "1001", 3, 155)));
        assertThat(basket.getAdjustments()).isEmpty();
        assertThat(basket.getTotalPence()).isEqualTo(465);
    }

    private static Basket basket(BasketLine... lines) {
        Basket basket = new Basket();
        basket.setBasketId("b-1");
        basket.getLines().addAll(List.of(lines));
        return basket;
    }

    private static BasketLine line(String lineId, String barcode, int quantity, int unitPricePence) {
        return new BasketLine(lineId, barcode, "SKU-" + barcode, "Item " + barcode, quantity, unitPricePence, 0);
    }
}
